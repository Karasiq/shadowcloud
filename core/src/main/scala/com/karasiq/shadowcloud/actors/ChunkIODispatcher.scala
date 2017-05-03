package com.karasiq.shadowcloud.actors

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, Props}
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperation}
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.{CategorizedRepository, StorageIOResult}
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.ByteStringConcat
import com.karasiq.shadowcloud.utils.HexString

object ChunkIODispatcher {
  case class ChunkPath(region: String, id: ByteString) {
    override def toString: String = {
      val sb = new StringBuilder(region.length + 1 + (id.length * 2))
      (sb ++= region += '/' ++= HexString.encode(id)).result()
    }
  }

  // Messages
  sealed trait Message
  case class WriteChunk(path: ChunkPath, chunk: Chunk) extends Message
  object WriteChunk extends MessageStatus[(ChunkPath, Chunk), Chunk]

  case class ReadChunk(path: ChunkPath, chunk: Chunk) extends Message
  object ReadChunk extends MessageStatus[(ChunkPath, Chunk), Chunk]

  case class DeleteChunks(chunks: Set[ChunkPath]) extends Message
  object DeleteChunks extends MessageStatus[Set[ChunkPath], StorageIOResult]

  case object GetKeys extends Message with MessageStatus[NotUsed, Set[ChunkPath]]

  // Props
  def props(repository: CategorizedRepository[String, ByteString]): Props = {
    Props(classOf[ChunkIODispatcher], repository)
  }
}

private final class ChunkIODispatcher(repository: CategorizedRepository[String, ByteString]) extends Actor with ActorLogging {
  import context.dispatcher

  import ChunkIODispatcher._
  implicit val actorMaterializer = ActorMaterializer()
  val chunksWrite = PendingOperation.withRegionChunk
  val chunksRead = PendingOperation.withRegionChunk

  def receive: Receive = {
    case WriteChunk(path, chunk) ⇒
      chunksWrite.addWaiter((path, chunk), sender(), () ⇒ writeChunk(path, chunk))

    case ReadChunk(path, chunk) ⇒
      chunksRead.addWaiter((path, chunk), sender(), () ⇒ readChunk(path, chunk))

    case msg: WriteChunk.Status ⇒
      chunksWrite.finish(msg.key, msg)

    case msg: ReadChunk.Status ⇒
      chunksRead.finish(msg.key, msg)

    case DeleteChunks(chunks) ⇒
      deleteChunks(chunks).pipeTo(sender())

    case GetKeys ⇒
      listKeys().pipeTo(sender())
  }

  private[this] def subRepository(region: String) = {
    repository.subRepository(region)
  }

  private def listKeys(): Future[GetKeys.Status] = {
    val future = repository.keys.map(ChunkPath.tupled).runFold(Set.empty[ChunkPath])(_ + _)
    GetKeys.wrapFuture(NotUsed, future)
  }

  private[this] def writeChunk(path: ChunkPath, chunk: Chunk): Unit = {
    val localRepository = subRepository(path.region)
    val writeSink = localRepository.write(path.id)
    val future = Source.single(chunk.data.encrypted).runWith(writeSink)
    future.onComplete {
      case Success(StorageIOResult.Success(storagePath, written)) ⇒
        log.debug("{} bytes written to {}, chunk: {} ({})", written, storagePath, chunk, path)
        self ! WriteChunk.Success((path, chunk), chunk)

      case Success(StorageIOResult.Failure(storagePath, error)) ⇒
        log.error(error, "Chunk write error to {}: {} ({})", storagePath, chunk, path)
        self ! WriteChunk.Failure((path, chunk), error)

      case Failure(error) ⇒
        log.error(error, "Chunk write error: {}", chunk)
        self ! WriteChunk.Failure((path, chunk), error)
    }
  }

  private[this] def readChunk(path: ChunkPath, chunk: Chunk): Unit = {
    val localRepository = subRepository(path.region)
    val readSource = localRepository.read(path.id)
    val (ioFuture, chunkFuture) = readSource
      .via(ByteStringConcat())
      .map(bs ⇒ chunk.copy(data = chunk.data.copy(encrypted = bs)))
      .toMat(Sink.head)(Keep.both)
      .run()

    ioFuture.onComplete {
      case Success(StorageIOResult.Success(storagePath, bytes)) ⇒
        chunkFuture.onComplete {
          case Success(chunkWithData) ⇒
            log.debug("{} bytes read from {}, chunk: {}", bytes, storagePath, chunkWithData)
            self ! ReadChunk.Success((path, chunk), chunkWithData)

          case Failure(error) ⇒
            log.error(error, "Chunk read error: {}", chunk)
            self ! ReadChunk.Failure((path, chunk), error)
        }

      case Success(StorageIOResult.Failure(storagePath, error)) ⇒
        log.error(error, "Chunk read error from {}: {}", storagePath, chunk)
        self ! ReadChunk.Failure((path, chunk), error)

      case Failure(error) ⇒
        log.error(error, "Chunk read error: {}", chunk)
        self ! ReadChunk.Failure((path, chunk), error)
    }
  }

  private[this] def deleteChunks(paths: Set[ChunkPath]): Future[DeleteChunks.Status] = {
    val byRegion = paths.groupBy(_.region).toVector.flatMap { case (region, chunks) ⇒
      val localRepository = subRepository(region)
      chunks.map(localRepository → _)
    }
    val result = Source(byRegion)
      .mapAsync(4) { case (lr, path) ⇒ lr.delete(path.id) }
      .toMat(Sink.seq)(Keep.right)
      .mapMaterializedValue(_.map(StorageUtils.foldIOResults))
      .run()
    DeleteChunks.wrapFuture(paths, StorageUtils.unwrapFuture(result))
  }
}
