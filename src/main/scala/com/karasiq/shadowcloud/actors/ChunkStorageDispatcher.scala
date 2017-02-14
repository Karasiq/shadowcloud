package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, ActorRef, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.{BaseChunkRepository, ChunkRepository}
import com.karasiq.shadowcloud.utils.MessageStatus

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ChunkStorageDispatcher {
  // Messages
  sealed trait Message
  case class WriteChunk(chunk: Chunk) extends Message
  object WriteChunk extends MessageStatus[Chunk, Chunk]

  case class ReadChunk(chunk: Chunk) extends Message
  object ReadChunk extends MessageStatus[Chunk, Source[ByteString, _]]

  // Props
  def props(chunkDispatcher: ActorRef, baseChunkRepository: BaseChunkRepository): Props = {
    Props(classOf[ChunkStorageDispatcher], chunkDispatcher, baseChunkRepository)
  }
}

class ChunkStorageDispatcher(chunkDispatcher: ActorRef, baseChunkRepository: BaseChunkRepository) extends Actor {
  import ChunkStorageDispatcher._

  implicit val actorMaterializer = ActorMaterializer()
  val pending = mutable.AnyRefMap[Chunk, Set[ActorRef]]()
  val chunkRepository = ChunkRepository.hashed(baseChunkRepository)

  def receive: Receive = {
    case ReadChunk(chunk) ⇒
      sender() ! ReadChunk.Success(chunk, chunkRepository.read(chunk.checksum.hash))

    case WriteChunk(chunk) ⇒
      if (!pending.contains(chunk)) {
        pending += chunk → Set(sender())
        writeChunk(chunk)
      } else {
        pending += chunk → (pending(chunk) + sender())
      }

    case msg @ WriteChunk.Failure(chunk, _) ⇒
      for (actors ← pending.remove(chunk); actor ← actors) {
        actor ! msg
      }

    case msg @ WriteChunk.Success(_, chunk) ⇒
      for (actors ← pending.remove(chunk); actor ← actors) {
        actor ! msg
      }
  }

  def writeChunk(chunk: Chunk): Unit = {
    Source.single(chunk.data.encrypted)
      .alsoTo(Sink.onComplete {
        case Success(Done) ⇒
          self ! WriteChunk.Success(chunk, chunk)

        case Failure(error) ⇒
          self ! WriteChunk.Failure(chunk, error)
      })
      .runWith(chunkRepository.write(chunk.checksum.hash))
  }

  override def preStart(): Unit = {
    super.preStart()
    context.watch(chunkDispatcher)
  }
}
