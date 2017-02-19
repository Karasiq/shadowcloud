package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.internal.PendingOperation
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.ChunkRepository
import com.karasiq.shadowcloud.storage.ChunkRepository.BaseChunkRepository

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
  def props(baseChunkRepository: BaseChunkRepository): Props = {
    Props(classOf[ChunkIODispatcher], baseChunkRepository)
  }
}

class ChunkIODispatcher(baseChunkRepository: BaseChunkRepository) extends Actor {
  import ChunkIODispatcher._
  implicit val actorMaterializer = ActorMaterializer()
  val pending = PendingOperation.chunk
  val chunkRepository = ChunkRepository.hexString(baseChunkRepository)
  val config = AppConfig().storage

  def receive: Receive = {
    case ReadChunk(chunk) ⇒
      sender() ! ReadChunk.Success(chunk, chunkRepository.read(config.chunkKey(chunk)))

    case WriteChunk(chunk) ⇒
      pending.addWaiter(chunk, sender(), () ⇒ writeChunk(chunk))

    case msg: WriteChunk.Status ⇒
      pending.finish(msg.key, msg)
  }

  private[this] def writeChunk(chunk: Chunk): Unit = {
    Source.single(chunk.data.encrypted)
      .alsoTo(Sink.onComplete {
        case Success(Done) ⇒
          self ! WriteChunk.Success(chunk, chunk)

        case Failure(error) ⇒
          self ! WriteChunk.Failure(chunk, error)
      })
      .runWith(chunkRepository.write(config.chunkKey(chunk)))
  }
}
