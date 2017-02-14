package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.events.RegionEvent.RegionEnvelope
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.actors.events.{RegionEvent, StorageEvent}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, StorageTracker}
import com.karasiq.shadowcloud.storage.IndexMerger
import com.karasiq.shadowcloud.storage.IndexMerger.RegionKey

import scala.language.postfixOps

object VirtualRegionDispatcher {
  // Messages
  sealed trait Message
  case class Register(storageId: String, dispatcher: ActorRef) extends Message

  // Props 
  def props(regionId: String): Props = {
    Props(classOf[VirtualRegionDispatcher], regionId)
  }
}

// TODO: Folder dispatcher
class VirtualRegionDispatcher(regionId: String) extends Actor with ActorLogging {
  import VirtualRegionDispatcher._
  require(regionId.nonEmpty)
  val storages = new StorageTracker
  val chunks = new ChunksTracker(storages, log)
  val merger = IndexMerger.region

  def receive: Receive = {
    case ReadChunk(chunk) ⇒
      chunks.readChunk(chunk, sender())

    case WriteChunk(chunk) ⇒
      chunks.writeChunk(chunk, sender())

    case _: WriteChunk.Success ⇒
      // Ignore

    case WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failed: {}", chunk)
      chunks.unregisterChunk(sender(), chunk)
      chunks.retryPendingChunks()

    case Register(storageId, dispatcher) if !storages.contains(storageId) ⇒
      log.info("Registered storage: {}", dispatcher)
      storages.register(storageId, dispatcher)
      chunks.retryPendingChunks()

    // Storage events
    case StorageEnvelope(storageId, event) if storages.contains(storageId) ⇒ event match {
      case StorageEvent.IndexLoaded(diffs) ⇒
        val dispatcher = storages.getDispatcher(storageId)
        log.info("Storage index loaded {}: {} diffs", dispatcher, diffs.length)
        merger.remove(merger.diffs.keySet.toSet.filter(_.indexId == storageId))
        diffs.foreach { case (sequenceNr, diff) ⇒
          merger.add(RegionKey(diff.time, storageId, sequenceNr), diff)
        }

      case StorageEvent.IndexUpdated(sequenceNr, diff, _) ⇒
        val dispatcher = storages.getDispatcher(storageId)
        log.info("Storage index updated {}: {}", storageId, diff)
        merger.add(RegionKey(diff.time, storages.getStorageId(dispatcher), sequenceNr), diff)
        chunks.update(dispatcher, diff.chunks)

      case StorageEvent.PendingIndexUpdated(_) ⇒
        // Ignore

      case StorageEvent.ChunkWritten(chunk) ⇒
        log.info("Chunk written: {}", chunk)
        chunks.registerChunk(storages.getDispatcher(storageId), chunk)
        RegionEvent.stream.publish(RegionEnvelope(regionId, RegionEvent.ChunkWritten(storageId, chunk)))
    }

    case Terminated(dispatcher) ⇒
      log.debug("Watched actor terminated: {}", dispatcher)
      if (storages.contains(dispatcher)) {
        val storageId = storages.getStorageId(dispatcher)
        merger.remove(merger.diffs.keySet.toSet.filter(_.indexId == storageId))
        storages.unregister(dispatcher)
      }
      chunks.unregister(dispatcher)
  }

  override def postStop(): Unit = {
    StorageEvent.stream.unsubscribe(self)
    super.postStop()
  }
}
