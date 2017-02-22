package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.scaladsl.Source
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.internal.PendingOperation
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.ChunkRepository
import com.karasiq.shadowcloud.storage.ChunkRepository.BaseChunkRepository

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

object ChunkIODispatcher {
  // Messages
  sealed trait Message
  case class WriteChunk(chunk: Chunk) extends Message
  object WriteChunk extends MessageStatus[Chunk, Chunk]

  case class ReadChunk(chunk: Chunk) extends Message
  object ReadChunk extends MessageStatus[Chunk, Source[ByteString, Future[IOResult]]]

  // Props
  def props(baseChunkRepository: BaseChunkRepository): Props = {
    Props(classOf[ChunkIODispatcher], baseChunkRepository)
  }
}

class ChunkIODispatcher(baseChunkRepository: BaseChunkRepository) extends Actor with ActorLogging {
  import ChunkIODispatcher._
  import context.dispatcher
  implicit val actorMaterializer = ActorMaterializer()
  val chunksWrite = PendingOperation.withChunk
  val chunkRepository = ChunkRepository.hexString(baseChunkRepository)
  val config = AppConfig().storage

  def receive: Receive = {
    case ReadChunk(chunk) ⇒
      val stream = chunkRepository.read(config.chunkKey(chunk))
      sender() ! ReadChunk.Success(chunk, stream)

    case WriteChunk(chunk) ⇒
      chunksWrite.addWaiter(chunk, sender(), () ⇒ writeChunk(chunk))

    case msg: WriteChunk.Status ⇒
      chunksWrite.finish(msg.key, msg)
  }

  private[this] def writeChunk(chunk: Chunk): Unit = {
    val ioResultFuture = Source.single(chunk.data.encrypted)
      .runWith(chunkRepository.write(config.chunkKey(chunk)))

    def onSuccess(written: Long): Unit = {
      log.debug("{} bytes written, chunk: {}", written, chunk)
      self ! WriteChunk.Success(chunk, chunk)
    }

    def onFailure(error: Throwable): Unit = {
      log.error(error, "Chunk write error: {}", chunk)
      self ! WriteChunk.Failure(chunk, error)
    }

    ioResultFuture.onComplete {
      case Success(IOResult(written, Success(Done))) ⇒
        onSuccess(written)

      case Success(IOResult(_, Failure(error))) ⇒
        onFailure(error)

      case Failure(error) ⇒
        onFailure(error)
    }
  }
}
