package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, Props}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import com.karasiq.shadowcloud.actors.internal.{AkkaUtils, PendingOperation}
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.Repository.BaseRepository
import com.karasiq.shadowcloud.storage.wrappers.RepositoryWrappers
import com.karasiq.shadowcloud.streams.ByteStringConcat

import scala.language.postfixOps
import scala.util.{Failure, Success}

object ChunkIODispatcher {
  // Messages
  sealed trait Message
  case class WriteChunk(chunk: Chunk) extends Message
  object WriteChunk extends MessageStatus[Chunk, Chunk]

  case class ReadChunk(chunk: Chunk) extends Message
  object ReadChunk extends MessageStatus[Chunk, Chunk]

  // Props
  def props(repository: BaseRepository): Props = {
    Props(classOf[ChunkIODispatcher], repository)
  }
}

class ChunkIODispatcher(repository: BaseRepository) extends Actor with ActorLogging {
  import ChunkIODispatcher._
  import context.dispatcher
  implicit val actorMaterializer = ActorMaterializer()
  val chunksWrite = PendingOperation.withChunk
  val chunksRead = PendingOperation.withChunk
  val chunkRepository = RepositoryWrappers.hexString(repository)
  val config = AppConfig().storage

  def receive: Receive = {
    case WriteChunk(chunk) ⇒
      chunksWrite.addWaiter(chunk, sender(), () ⇒ writeChunk(chunk))

    case ReadChunk(chunk) ⇒
      chunksRead.addWaiter(chunk, sender(), () ⇒ readChunk(chunk))

    case msg: WriteChunk.Status ⇒
      chunksWrite.finish(msg.key, msg)

    case msg: ReadChunk.Status ⇒
      chunksRead.finish(msg.key, msg)
  }

  private[this] def writeChunk(chunk: Chunk): Unit = {
    val key = config.chunkKey(chunk)
    val writeSink = chunkRepository.write(key)
    val future = Source.single(chunk.data.encrypted).runWith(writeSink)
    AkkaUtils.onIOComplete(future) {
      case Success(written) ⇒
        log.debug("{} bytes written, chunk: {}", written, chunk)
        self ! WriteChunk.Success(chunk, chunk)

      case Failure(error) ⇒
        log.error(error, "Chunk write error: {}", chunk)
        self ! WriteChunk.Failure(chunk, error)
    }
  }

  private[this] def readChunk(chunk: Chunk): Unit = {
    val key = config.chunkKey(chunk)
    val readSource = chunkRepository.read(key)
    val (ioResult, future) = readSource
      .via(ByteStringConcat())
      .map(bs ⇒ chunk.copy(data = chunk.data.copy(encrypted = bs)))
      .toMat(Sink.head)(Keep.both)
      .run()

    AkkaUtils.unwrapIOResult(ioResult).zip(future).onComplete {
      case Success((bytes, chunkWithData)) ⇒
        log.debug("{} bytes read, chunk: {}", bytes, chunkWithData)
        self ! ReadChunk.Success(chunk, chunkWithData)

      case Failure(error) ⇒
        log.error(error, "Chunk write error: {}", chunk)
        self ! ReadChunk.Failure(chunk, error)
    }
  }
}
