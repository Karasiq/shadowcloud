package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, ActorRef}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndex, ChunkIndexDiff}
import com.karasiq.shadowcloud.storage.ChunkRepository

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.{Failure, Success}

object StorageDispatcher {
  case class LoadChunks(diff: ChunkIndexDiff)

  case class WriteChunk(chunk: Chunk)
  object WriteChunk {
    sealed trait Status
    case class Success(chunk: Chunk) extends Status
    case class Failure(chunk: Chunk, error: Throwable) extends Status
  }

  case class ReadChunk(chunk: Chunk)
  object ReadChunk {
    sealed trait Status
    case class Success(chunk: Chunk, source: Source[ByteString, _]) extends Status
    case class Failure(chunk: Chunk, error: Throwable) extends Status
  }
}

class StorageDispatcher(chunkRepository: ChunkRepository, chunkDispatcher: ActorRef) extends Actor {
  import StorageDispatcher._

  implicit val actorMaterializer = ActorMaterializer()
  var index = ChunkIndex.empty
  val pending = mutable.Set[Chunk]()

  def receive = {
    case LoadChunks(diff) ⇒
      index = index.patch(diff)
      chunkDispatcher ! ChunkDispatcher.Update(diff)

    case ReadChunk(chunk) ⇒
      val sender = context.sender()
      if (index.contains(chunk.withoutData)) {
        sender ! ReadChunk.Success(chunk, chunkRepository.read(chunk.checksum.hash))
      } else {
        sender ! ReadChunk.Failure(chunk, new IllegalArgumentException(s"Chunk not found: $chunk"))
      }

    case WriteChunk(chunk) ⇒
      if (!pending.contains(chunk)) {
        pending += chunk
        writeChunk(chunk)
      }

    case failure @ WriteChunk.Failure(chunk, exc) ⇒
      pending -= chunk
      chunkDispatcher ! failure

    case success @ WriteChunk.Success(chunk) ⇒
      pending -= chunk
      chunkDispatcher ! success
      // TODO: Persist
      index = index.addChunks(chunk.withoutData)
  }

  def loadIndex(): Unit = {
    // TODO: Index loader
    chunkDispatcher ! ChunkDispatcher.Register(index)
  }

  def writeChunk(chunk: Chunk): Unit = {
    Source.single(chunk.data.encrypted)
      .alsoTo(Sink.onComplete {
        case Success(Done) ⇒
          self ! WriteChunk.Success(chunk)

        case Failure(exc) ⇒
          self ! WriteChunk.Failure(chunk, exc)
      })
      .runWith(chunkRepository.write(chunk.checksum.hash))
  }

  override def preStart() = {
    super.preStart()
    context.watch(chunkDispatcher)
    loadIndex()
  }
}
