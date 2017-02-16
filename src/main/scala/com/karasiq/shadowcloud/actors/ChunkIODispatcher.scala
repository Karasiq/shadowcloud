package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.internal.PendingOperation
import com.karasiq.shadowcloud.actors.utils.{ChunkKeyExtractor, MessageStatus}
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.{BaseChunkRepository, ChunkRepository}

import scala.language.postfixOps
import scala.util.{Failure, Success}

object ChunkIODispatcher {
  // Messages
  sealed trait Message
  case class WriteChunk(chunk: Chunk) extends Message
  object WriteChunk extends MessageStatus[Chunk, Chunk]

  case class ReadChunk(chunk: Chunk) extends Message
  object ReadChunk extends MessageStatus[Chunk, Source[ByteString, _]]

  // Props
  def props(baseChunkRepository: BaseChunkRepository, keyExtractor: ChunkKeyExtractor = ChunkKeyExtractor.hash): Props = {
    Props(classOf[ChunkIODispatcher], baseChunkRepository, keyExtractor)
  }
}

class ChunkIODispatcher(baseChunkRepository: BaseChunkRepository, keyExtractor: ChunkKeyExtractor) extends Actor {
  import ChunkIODispatcher._

  implicit val actorMaterializer = ActorMaterializer()
  val pending = PendingOperation.chunk
  val chunkRepository = ChunkRepository.hexString(baseChunkRepository)

  def receive: Receive = {
    case ReadChunk(chunk) ⇒
      sender() ! ReadChunk.Success(chunk, chunkRepository.read(keyExtractor.key(chunk)))

    case WriteChunk(chunk) ⇒
      pending.addWaiter(chunk, sender(), () ⇒ writeChunk(chunk))

    case msg: WriteChunk.Status ⇒
      pending.finish(msg.key, msg)
  }

  def writeChunk(chunk: Chunk): Unit = {
    Source.single(chunk.data.encrypted)
      .alsoTo(Sink.onComplete {
        case Success(Done) ⇒
          self ! WriteChunk.Success(chunk, chunk)

        case Failure(error) ⇒
          self ! WriteChunk.Failure(chunk, error)
      })
      .runWith(chunkRepository.write(keyExtractor.key(chunk)))
  }
}
