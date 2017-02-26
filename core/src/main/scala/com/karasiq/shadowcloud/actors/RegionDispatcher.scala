package com.karasiq.shadowcloud.actors

import java.io.FileNotFoundException

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.events.{RegionEvents, StorageEvents}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, StorageTracker}
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.index.{File, Folder, FolderIndex, Path}
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object RegionDispatcher {
  // Messages
  sealed trait Message
  case class Register(storageId: String, dispatcher: ActorRef, health: StorageHealth = StorageHealth.empty) extends Message
  case class Unregister(storageId: String) extends Message
  case class WriteIndex(diff: FolderIndexDiff) extends Message
  object WriteIndex extends MessageStatus[IndexDiff, IndexDiff]
  case object GetIndex extends Message {
    case class Success(diffs: Seq[(RegionKey, IndexDiff)])
  }
  case object Synchronize extends Message
  case class GetFiles(path: Path) extends Message
  object GetFiles extends MessageStatus[Path, Set[File]]
  case class GetFolder(path: Path) extends Message
  object GetFolder extends MessageStatus[Path, Folder]

  // Internal messages
  private case class PushDiffs(storageId: String, diffs: Seq[(Long, IndexDiff)], pending: IndexDiff) extends Message

  // Props
  def props(regionId: String): Props = {
    Props(classOf[RegionDispatcher], regionId)
  }
}

class RegionDispatcher(regionId: String) extends Actor with ActorLogging {
  import RegionDispatcher._
  require(regionId.nonEmpty)
  implicit val executionContext: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(10 seconds)
  val config = AppConfig()
  val storages = StorageTracker()
  val chunks = ChunksTracker(config.storage, storages, log)
  val merger = IndexMerger.region

  def receive: Receive = {
    // -----------------------------------------------------------------------
    // Global index commands
    // -----------------------------------------------------------------------
    case WriteIndex(folders) ⇒
      val diff = IndexDiff(Utils.timestamp, folders)
      val actors = Utils.takeOrAll(storages.forIndexWrite(diff), config.index.replicationFactor)
      if (actors.isEmpty) {
        log.warning("No index storages available on {}", regionId)
        sender() ! WriteIndex.Failure(diff, new IllegalStateException("No storages available"))
      } else {
        merger.addPending(diff)
        log.debug("Writing to virtual region [{}] index: {} (storages = {})", regionId, diff, actors)
        actors.foreach(_ ! IndexDispatcher.AddPending(diff))
        sender() ! WriteIndex.Success(diff, merger.pending)
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
      sender() ! GetIndex.Success(merger.diffs.toVector)

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

    case WriteChunk.Success(_, chunk) ⇒
      log.debug("Chunk write success: {}", chunk)

    case WriteChunk.Failure(chunk, error) ⇒
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
      val indexFuture = (dispatcher ? IndexDispatcher.GetIndex).mapTo[IndexDispatcher.GetIndex.Success]
      indexFuture.onComplete {
        case Success(IndexDispatcher.GetIndex.Success(diffs, pending)) ⇒
          self ! PushDiffs(storageId, diffs, pending)

        case Failure(error) ⇒
          log.error(error, "Error fetching index: {}", dispatcher)
      }

    case Unregister(storageId) if storages.contains(storageId) ⇒
      val dispatcher = storages.getDispatcher(storageId)
      dropStorageDiffs(storageId)
      storages.unregister(dispatcher)
      chunks.unregister(dispatcher)

    case PushDiffs(storageId, diffs, pending) if storages.contains(storageId) ⇒
      merger.addPending(pending)
      addStorageDiffs(storageId, diffs)
      chunks.retryPendingChunks()

    case StorageEnvelope(storageId, event: StorageEvents.Event) if storages.contains(storageId) ⇒ event match {
      case StorageEvents.IndexLoaded(diffs) ⇒
        log.info("Storage [{}] index loaded: {} diffs", storageId, diffs.length)
        dropStorageDiffs(storageId)
        chunks.unregister(storages.getDispatcher(storageId))
        addStorageDiffs(storageId, diffs)

      case StorageEvents.IndexUpdated(sequenceNr, diff, _) ⇒
        log.debug("Storage [{}] index updated: {}", storageId, diff)
        addStorageDiff(storageId, sequenceNr, diff)

      case StorageEvents.PendingIndexUpdated(diff) ⇒
        log.debug("Storage [{}] pending index updated: {}", storageId, diff)
        merger.addPending(diff)

      case StorageEvents.ChunkWritten(chunk) ⇒
        log.debug("Chunk written: {}", chunk)
        chunks.registerChunk(storages.getDispatcher(storageId), chunk)
        RegionEvents.stream.publish(RegionEnvelope(regionId, RegionEvents.ChunkWritten(storageId, chunk)))

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
      merger.add(regionKey, diff)
      log.debug("Virtual region [{}] index updated: {} -> {}", regionId, regionKey, diff)
      RegionEvents.stream.publish(RegionEnvelope(regionId, RegionEvents.IndexUpdated(regionKey, diff)))
    }
  }

  @inline
  private[this] def addStorageDiff(storageId: String, sequenceNr: Long, diff: IndexDiff) = {
    addStorageDiffs(storageId, Array((sequenceNr, diff)))
  }

  private[this] def dropStorageDiffs(storageId: String): Unit = {
    merger.remove(merger.diffs.keySet.toSet.filter(_.indexId == storageId))
  }

  private[this] def folderIndex: FolderIndex = {
    merger.folders.patch(merger.pending.folders)
  }

  override def postStop(): Unit = {
    StorageEvents.stream.unsubscribe(self)
    super.postStop()
  }
}
