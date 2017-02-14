package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, ActorRefFactory, Props}
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.actors.internal.PendingOperation
import com.karasiq.shadowcloud.index.{ChunkIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage.{BaseChunkRepository, BaseIndexRepository}
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

object StorageDispatcher {
  // Props
  def props(storageId: String, index: ActorRef, chunkIO: ActorRef): Props = {
    Props(classOf[StorageDispatcher], storageId, index, chunkIO)
  }

  def create(storageId: String, index: BaseIndexRepository, chunks: BaseChunkRepository)(implicit arf: ActorRefFactory): ActorRef = {
    val indexSynchronizer = arf.actorOf(IndexSynchronizer.props(storageId, index), "indexSynchronizer")
    val chunkIODispatcher = arf.actorOf(ChunkIODispatcher.props(chunks), "chunkIODispatcher")
    arf.actorOf(props(storageId, indexSynchronizer, chunkIODispatcher), "storageDispatcher")
  }
}

class StorageDispatcher(storageId: String, index: ActorRef, chunkIO: ActorRef) extends Actor with ActorLogging {
  val pending = PendingOperation.chunk

  def receive: Receive = {
    case msg @ ChunkIODispatcher.WriteChunk.Success(_, chunk) ⇒
      log.debug("Chunk written, appending to index: {}", chunk)
      pending.finish(chunk, msg)
      StorageEvent.stream.publish(StorageEnvelope(storageId, StorageEvent.ChunkWritten(chunk)))
      index ! IndexSynchronizer.AddPending(IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff(Set(chunk.withoutData))))

    case msg @ ChunkIODispatcher.WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failure: {}", chunk)
      pending.finish(chunk, msg)

    case msg @ ChunkIODispatcher.WriteChunk(chunk) ⇒
      pending.addWaiter(chunk, sender(), { () ⇒
        log.debug("Writing chunk: {}", chunk)
        chunkIO ! msg
      })

    case msg @ ChunkIODispatcher.ReadChunk(chunk) ⇒
      log.debug("Reading chunk: {}", chunk)
      chunkIO.forward(msg)

    case msg: IndexSynchronizer.Message ⇒
      index.forward(msg)
  }

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    context.watch(chunkIO)
    context.watch(index)
  }
}
