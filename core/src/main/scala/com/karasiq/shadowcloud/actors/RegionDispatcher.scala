package com.karasiq.shadowcloud.actors

import java.io.FileNotFoundException

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, ReadChunk => SReadChunk, WriteChunk => SWriteChunk}
import com.karasiq.shadowcloud.actors.events.{RegionEvents, SCEvents, StorageEvents}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, StorageTracker}
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.utils.Utils

object RegionDispatcher {
  // Messages
  sealed trait Message
  case class Register(storageId: String, dispatcher: ActorRef, health: StorageHealth = StorageHealth.empty) extends Message
  case class Unregister(storageId: String) extends Message

  case class WriteIndex(diff: FolderIndexDiff) extends Message
  object WriteIndex extends MessageStatus[FolderIndexDiff, IndexDiff]
  case object GetIndex extends Message with MessageStatus[String, IndexMerger.State[RegionKey]]
  case object Synchronize extends Message
  case class GetFiles(path: Path) extends Message
  object GetFiles extends MessageStatus[Path, Set[File]]
  case class GetFolder(path: Path) extends Message
  object GetFolder extends MessageStatus[Path, Folder]

  case class WriteChunk(chunk: Chunk) extends Message
  case object WriteChunk extends MessageStatus[Chunk, Chunk]
  case class ReadChunk(chunk: Chunk) extends Message
  case object ReadChunk extends MessageStatus[Chunk, Chunk]

  // Internal messages
  private case class PushDiffs(storageId: String, diffs: Seq[(Long, IndexDiff)], pending: IndexDiff) extends Message

  // Props
  def props(regionId: String): Props = {
    Props(classOf[RegionDispatcher], regionId)
  }
}

private final class RegionDispatcher(regionId: String) extends Actor with ActorLogging {
  import RegionDispatcher._
  require(regionId.nonEmpty)
  private[this] implicit val executionContext: ExecutionContext = context.dispatcher
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] val events = SCEvents()
  private[this] val config = AppConfig()
  private[this] val modules = ModuleRegistry(config)
  private[this] val storages = StorageTracker()
  private[this] val chunks = ChunksTracker(regionId, config.storage, modules, storages, log)
  private[this] val globalIndex = IndexMerger.region

  def receive: Receive = {
    // -----------------------------------------------------------------------
    // Global index commands
    // -----------------------------------------------------------------------
    case WriteIndex(folders) ⇒
      if (folders.isEmpty) {
        sender() ! WriteIndex.Failure(folders, new IllegalArgumentException("Diff is empty"))
      } else {
        val diff = IndexDiff(Utils.timestamp, folders)
        val actors = Utils.takeOrAll(storages.forIndexWrite(diff), config.index.replicationFactor)
        if (actors.isEmpty) {
          log.warning("No index storages available on {}", regionId)
          sender() ! WriteIndex.Failure(folders, new IllegalStateException("No storages available"))
        } else {
          globalIndex.addPending(diff)
          log.debug("Writing to virtual region [{}] index: {} (storages = {})", regionId, diff, actors)
          actors.foreach(_ ! IndexDispatcher.AddPending(regionId, diff))
          sender() ! WriteIndex.Success(folders, globalIndex.pending)
        }
      }

    case GetFiles(path) ⇒
      val files = folderIndex
        .get(path.parent)
        .map(_.files.filter(_.path == path))
        .filter(_.nonEmpty)
      files match {
        case Some(files) ⇒
          sender() ! GetFiles.Success(path, files)

        case None ⇒
          sender() ! GetFiles.Failure(path, new FileNotFoundException(s"No such file: $path"))
      }

    case GetFolder(path) ⇒
      folderIndex.get(path) match {
        case Some(folder) ⇒
          sender() ! GetFolder.Success(path, folder)

        case None ⇒
          sender() ! GetFolder.Failure(path, new FileNotFoundException(s"No such directory: $path"))
      }

    case GetIndex ⇒
      sender() ! GetIndex.Success(regionId, IndexMerger.state(globalIndex))

    case Synchronize ⇒
      log.info("Force synchronizing indexes of virtual region: {}", regionId)
      storages.available().foreach(_ ! IndexDispatcher.Synchronize)

    // -----------------------------------------------------------------------
    // Read/write commands
    // -----------------------------------------------------------------------
    case ReadChunk(chunk) ⇒
      chunks.readChunk(chunk, sender())

    case WriteChunk(chunk) ⇒
      chunks.writeChunk(chunk, sender())

    case SReadChunk.Success((ChunkPath(`regionId`, _), _), chunk) ⇒
      log.debug("Chunk read success: {}", chunk)
      chunks.readSuccess(chunk)

    case SReadChunk.Failure((ChunkPath(`regionId`, _), chunk), error) ⇒
      log.error(error, "Chunk read failed: {}", chunk)
      chunks.readFailure(chunk, error)

    case SWriteChunk.Success((ChunkPath(`regionId`, _), _), chunk) ⇒
      log.debug("Chunk write success: {}", chunk)
      // chunks.registerChunk(sender(), chunk)

    case SWriteChunk.Failure((ChunkPath(`regionId`, _), chunk), error) ⇒
      log.error(error, "Chunk write failed: {}", chunk)
      chunks.unregisterChunk(sender(), chunk)
      chunks.retryPendingChunks()

    // -----------------------------------------------------------------------
    // Storage events
    // -----------------------------------------------------------------------
    case Register(storageId, dispatcher, health) if !storages.contains(storageId) ⇒
      log.info("Registered storage: {} -> {} [{}]", storageId, dispatcher, health)
      storages.register(storageId, dispatcher, health)
      if (health == StorageHealth.empty) dispatcher ! StorageDispatcher.CheckHealth
      val future = IndexDispatcher.GetIndex.unwrapFuture(dispatcher ? IndexDispatcher.GetIndex(regionId))
      future.onComplete {
        case Success(IndexMerger.State(diffs, pending)) ⇒
          self ! PushDiffs(storageId, diffs, pending)

        case Failure(_: StorageException.NotFound) ⇒
          val diff = globalIndex.mergedDiff.merge(globalIndex.pending)
          if (diff.nonEmpty) {
            log.info("Mirroring index to storage {}: {}", storageId, diff)
            dispatcher ! IndexDispatcher.AddPending(regionId, diff)
          }

        case Failure(error) ⇒
          log.error(error, "Error fetching index: {}", dispatcher)
      }

    case Unregister(storageId) if storages.contains(storageId) ⇒
      val dispatcher = storages.getDispatcher(storageId)
      dropStorageDiffs(storageId)
      storages.unregister(dispatcher)
      chunks.unregister(dispatcher)

    case PushDiffs(storageId, diffs, pending) if storages.contains(storageId) ⇒
      globalIndex.addPending(pending)
      addStorageDiffs(storageId, diffs)
      chunks.retryPendingChunks()

    case StorageEnvelope(storageId, event: StorageEvents.Event) if storages.contains(storageId) ⇒ event match {
      case StorageEvents.IndexLoaded(diffs) ⇒
        val localDiffs = diffs.get(regionId).fold(Nil: Seq[(Long, IndexDiff)])(_.diffs)
        log.info("Storage [{}] index loaded: {} diffs", storageId, localDiffs.length)
        dropStorageDiffs(storageId)
        chunks.unregister(storages.getDispatcher(storageId))
        addStorageDiffs(storageId, localDiffs)

      case StorageEvents.IndexUpdated(`regionId`, sequenceNr, diff, _) ⇒
        log.debug("Storage [{}] index updated: {}", storageId, diff)
        addStorageDiff(storageId, sequenceNr, diff)

      case StorageEvents.PendingIndexUpdated(`regionId`, diff) ⇒
        log.debug("Storage [{}] pending index updated: {}", storageId, diff)
        globalIndex.addPending(diff)

      case StorageEvents.IndexDeleted(`regionId`, sequenceNrs) ⇒
        log.debug("Diffs deleted from storage [{}]: {}", storageId, sequenceNrs)
        dropStorageDiffs(storageId, sequenceNrs)

      case StorageEvents.ChunkWritten(ChunkPath(`regionId`, _), chunk) ⇒
        log.debug("Chunk written: {}", chunk)
        chunks.registerChunk(storages.getDispatcher(storageId), chunk)
        events.region.publish(RegionEnvelope(regionId, RegionEvents.ChunkWritten(storageId, chunk)))

      case StorageEvents.HealthUpdated(health) ⇒
        log.debug("Storage [{}] health report: {}", storageId, health)
        storages.update(storageId, health)
        chunks.retryPendingChunks()

      case _ ⇒
        // Ignore
    }

    case Terminated(dispatcher) ⇒
      log.debug("Watched actor terminated: {}", dispatcher)
      if (storages.contains(dispatcher)) {
        val storageId = storages.getStorageId(dispatcher)
        dropStorageDiffs(storageId)
        storages.unregister(dispatcher)
      }
      chunks.unregister(dispatcher)
  }

  private[this] def addStorageDiffs(storageId: String, diffs: Seq[(Long, IndexDiff)]): Unit = {
    val dispatcher = storages.getDispatcher(storageId)
    diffs.foreach { case (sequenceNr, diff) ⇒
      chunks.update(dispatcher, diff.chunks)
      val regionKey = RegionKey(diff.time, storageId, sequenceNr)
      globalIndex.add(regionKey, diff)
      log.debug("Virtual region [{}] index updated: {} -> {}", regionId, regionKey, diff)
      events.region.publish(RegionEnvelope(regionId, RegionEvents.IndexUpdated(regionKey, diff)))
    }
  }

  @inline
  private[this] def addStorageDiff(storageId: String, sequenceNr: Long, diff: IndexDiff) = {
    addStorageDiffs(storageId, Seq((sequenceNr, diff)))
  }

  private[this] def dropStorageDiffs(storageId: String, sequenceNrs: Set[Long]): Unit = {
    val preDel = globalIndex.chunks
    val regionKeys = globalIndex.diffs.keys
      .filter(rk ⇒ rk.storageId == storageId && sequenceNrs.contains(rk.sequenceNr))
      .toSet
    globalIndex.delete(regionKeys)
    val deleted = globalIndex.chunks.diff(preDel).deletedChunks
    val dispatcher = storages.getDispatcher(storageId)
    deleted.foreach(chunks.unregisterChunk(dispatcher, _))
    events.region.publish(RegionEnvelope(regionId, RegionEvents.IndexDeleted(regionKeys)))
  }

  private[this] def dropStorageDiffs(storageId: String): Unit = {
    globalIndex.delete(globalIndex.diffs.keys.filter(_.storageId == storageId).toSet)
  }

  private[this] def folderIndex: FolderIndex = {
    globalIndex.folders.patch(globalIndex.pending.folders)
  }

  override def postStop(): Unit = {
    events.storage.unsubscribe(self)
    super.postStop()
  }
}
