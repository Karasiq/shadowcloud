package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage.{BaseChunkRepository, BaseIndexRepository}
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.mutable
import scala.language.postfixOps

object IndexedStorageDispatcher {
  // Messages
  sealed trait Message
  case class Subscribe(subscriber: ActorRef) extends Message

  // Props
  def props(indexId: String, chunkRepository: BaseChunkRepository, indexRepository: BaseIndexRepository): Props = {
    Props(classOf[IndexedStorageDispatcher], indexId, chunkRepository, indexRepository)
  }
}

class IndexedStorageDispatcher(indexId: String, chunkRepository: BaseChunkRepository, indexRepository: BaseIndexRepository) extends Actor with ActorLogging {
  import IndexedStorageDispatcher._
  val storage = context.actorOf(ChunkStorageDispatcher.props(self, chunkRepository), "chunksWriter")
  val index = context.actorOf(IndexSynchronizer.props(indexId, self, indexRepository), "indexSynchronizer")
  var subscribers = Set.empty[ActorRef]
  val pending = mutable.AnyRefMap[Chunk, Set[ActorRef]]()

  def receive: Receive = {
    case Subscribe(actor) if !subscribers.contains(actor) ⇒
      subscribers += actor
      index.tell(IndexSynchronizer.Get, actor)
      context.watch(actor)

    case Terminated(actor) if subscribers.contains(actor) ⇒
      subscribers -= actor
    
    case indexUpdate: IndexSynchronizer.Update ⇒
      subscribers.foreach(_ ! indexUpdate)

    case msg @ ChunkStorageDispatcher.WriteChunk.Success(_, chunk) ⇒
      log.debug("Chunk written, appending to index: {}", chunk)
      for (actors ← pending.remove(chunk); actor ← actors) actor ! msg
      index ! IndexSynchronizer.Append(IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff(Set(chunk))))

    case msg @ ChunkStorageDispatcher.WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failure: {}", chunk)
      for (actors ← pending.remove(chunk); actor ← actors) actor ! msg

    case msg @ ChunkStorageDispatcher.WriteChunk(chunk) ⇒
      if (pending.contains(chunk)) {
        pending += chunk → (pending(chunk) + sender())
      } else {
        pending += chunk → Set(sender())
        log.debug("Writing chunk: {}", chunk)
        storage ! msg
      }

    case msg @ ChunkStorageDispatcher.ReadChunk(chunk) ⇒
      log.debug("Reading chunk: {}", chunk)
      storage.forward(msg)

    case msg: IndexSynchronizer.Message ⇒
      index.forward(msg)
  }

  @scala.throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    super.preStart()
    context.watch(storage)
    context.watch(index)
  }
}
