package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.karasiq.shadowcloud.actors.ChunkStorageDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, StorageTracker}
import com.karasiq.shadowcloud.storage.IndexMerger

import scala.language.postfixOps

object VirtualRegionDispatcher {
  // Messages
  sealed trait Message
  case class Register(indexId: String, dispatcher: ActorRef) extends Message

  // Props 
  val props: Props = {
    Props(classOf[VirtualRegionDispatcher])
  }
}

// TODO: Folder dispatcher
class VirtualRegionDispatcher extends Actor with ActorLogging {
  import VirtualRegionDispatcher._
  val storages = new StorageTracker
  val chunks = new ChunksTracker(storages, log)
  val merger = IndexMerger.multiSeq

  def receive: Receive = {
    case ReadChunk(chunk) ⇒
      chunks.readChunk(chunk, sender())

    case WriteChunk(chunk) ⇒
      chunks.writeChunk(chunk, sender())

    case WriteChunk.Success(_, chunk) ⇒
      require(chunk.data.nonEmpty && chunk.checksum.hash.nonEmpty)
      log.info("Chunk write success: {}", chunk)
      chunks.registerChunk(sender(), chunk)

    case WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failed: {}", chunk)
      chunks.unregisterChunk(sender(), chunk)
      chunks.retryPendingChunks()

    case Register(indexId, dispatcher) if !storages.contains(sender()) ⇒
      log.info("Registered storage: {}", dispatcher)
      storages.register(indexId, dispatcher)
      dispatcher ! IndexedStorageDispatcher.Subscribe(self)
      chunks.retryPendingChunks()

    case IndexSynchronizer.Loaded(diffs) if storages.contains(sender()) ⇒
      val dispatcher = sender()
      val dispatcherId = storages.getIdOf(dispatcher)
      log.info("Storage index loaded {}: {} diffs", dispatcher, diffs.length)
      merger.remove(merger.diffs.keySet.toSet.filter(_._1 == dispatcherId))
      diffs.foreach { case (sequenceNr, diff) ⇒
        merger.add((dispatcherId, sequenceNr), diff)
      }

    case IndexSynchronizer.Update(sequenceNr, diff, _) if storages.contains(sender()) ⇒
      val dispatcher = sender()
      log.info("Storage index updated {}: {}", dispatcher, diff)
      require(storages.contains(dispatcher))
      merger.add((storages.getIdOf(dispatcher), sequenceNr), diff)
      chunks.update(dispatcher, diff.chunks)

    case Terminated(dispatcher) ⇒
      log.debug("Watched actor terminated: {}", dispatcher)
      if (storages.contains(dispatcher)) {
        val dispatcherId = storages.getIdOf(dispatcher)
        merger.remove(merger.diffs.keySet.toSet.filter(_._1 == dispatcherId))
        storages.unregister(dispatcher)
      }
      chunks.unregister(dispatcher)
  }
}
