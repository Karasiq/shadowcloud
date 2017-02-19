package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import akka.pattern.ask
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.events.RegionEvent.RegionEnvelope
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.actors.events.{RegionEvent, StorageEvent}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, StorageTracker}
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.{FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage.IndexMerger.RegionKey
import com.karasiq.shadowcloud.storage.{IndexMerger, StorageHealth}
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object VirtualRegionDispatcher {
  // Messages
  sealed trait Message
  case class Register(storageId: String, dispatcher: ActorRef, health: StorageHealth) extends Message
  case class WriteIndex(diff: FolderIndexDiff) extends Message
  object WriteIndex extends MessageStatus[IndexDiff, IndexDiff]
  case object GetIndex extends Message {
    case class Success(diffs: Seq[(RegionKey, IndexDiff)])
  }
  case object Synchronize extends Message

  // Internal messages
  private case class PushDiffs(storageId: String, diffs: Seq[(Long, IndexDiff)]) extends Message

  // Props 
  def props(regionId: String): Props = {
    Props(classOf[VirtualRegionDispatcher], regionId)
  }
}

class VirtualRegionDispatcher(regionId: String) extends Actor with ActorLogging {
  import VirtualRegionDispatcher._
  require(regionId.nonEmpty)
  implicit val executionContext: ExecutionContext = context.dispatcher
  implicit val timeout = Timeout(10 seconds)
  val config = AppConfig()
  val storages = new StorageTracker
  val chunks = new ChunksTracker(config.storage, storages, log)
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
        log.info("Writing to virtual region [{}] index: {} (storages = {})", regionId, diff, actors)
        actors.foreach(_ ! IndexSynchronizer.AddPending(diff))
        sender() ! WriteIndex.Success(diff, merger.pending)
      }

    case GetIndex ⇒
      sender() ! GetIndex.Success(merger.diffs.toVector)

    case Synchronize ⇒
      log.info("Force synchronizing indexes of virtual region: {}", regionId)
      storages.available().foreach(_ ! IndexSynchronizer.Synchronize)

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
      log.info("Registered storage: {}", dispatcher)
      storages.register(storageId, dispatcher, health)
      val indexFuture = (dispatcher ? IndexSynchronizer.GetIndex).mapTo[IndexSynchronizer.GetIndex.Success]
      indexFuture.onComplete {
        case Success(IndexSynchronizer.GetIndex.Success(diffs)) ⇒
          self ! PushDiffs(storageId, diffs)

        case Failure(error) ⇒
          log.error(error, "Error fetching index: {}", dispatcher)
      }

    case PushDiffs(storageId, diffs) if storages.contains(storageId) ⇒
      addStorageDiffs(storageId, diffs)
      chunks.retryPendingChunks()

    case StorageEnvelope(storageId, event) if storages.contains(storageId) ⇒ event match {
      case StorageEvent.IndexLoaded(diffs) ⇒
        log.info("Storage [{}] index loaded: {} diffs", storageId, diffs.length)
        dropStorageDiffs(storageId)
        addStorageDiffs(storageId, diffs)

      case StorageEvent.IndexUpdated(sequenceNr, diff, _) ⇒
        log.info("Storage [{}] index updated: {}", storageId, diff)
        addStorageDiff(storageId, sequenceNr, diff)

      case StorageEvent.PendingIndexUpdated(diff) ⇒
        log.debug("Storage [{}] pending index updated: {}", storageId, diff)
        merger.addPending(diff)

      case StorageEvent.ChunkWritten(chunk) ⇒
        log.info("Chunk written: {}", chunk)
        chunks.registerChunk(storages.getDispatcher(storageId), chunk)
        RegionEvent.stream.publish(RegionEnvelope(regionId, RegionEvent.ChunkWritten(storageId, chunk)))

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
      log.info("Virtual region [{}] index updated: {} -> {}", regionId, regionKey, diff)
      RegionEvent.stream.publish(RegionEnvelope(regionId, RegionEvent.IndexUpdated(regionKey, diff)))
    }
  }

  @inline
  private[this] def addStorageDiff(storageId: String, sequenceNr: Long, diff: IndexDiff) = {
    addStorageDiffs(storageId, Array((sequenceNr, diff)))
  }

  private[this] def dropStorageDiffs(storageId: String): Unit = {
    merger.remove(merger.diffs.keySet.toSet.filter(_.indexId == storageId))
  }

  override def postStop(): Unit = {
    StorageEvent.stream.unsubscribe(self)
    super.postStop()
  }
}
