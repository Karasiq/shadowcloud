package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, Props, Terminated}
import com.karasiq.shadowcloud.actors.StorageDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.actors.internal.{ChunksTracker, StorageTracker}
import com.karasiq.shadowcloud.index.{ChunkIndex, ChunkIndexDiff}

import scala.language.postfixOps

object ChunkDispatcher {
  case class Register(index: ChunkIndex)
  case class Update(chunks: ChunkIndexDiff)

  val props: Props = {
    Props(classOf[ChunkDispatcher])
  }
}

class ChunkDispatcher extends Actor with ActorLogging {
  import ChunkDispatcher._
  val storages = new StorageTracker(this)
  val chunks = new ChunksTracker(this, storages)

  def receive: Receive = {
    case ReadChunk(chunk) ⇒
      chunks.readChunk(chunk, sender())

    case WriteChunk(chunk) ⇒
      chunks.writeChunk(chunk, sender())

    case WriteChunk.Success(_, chunk) ⇒
      require(chunk.data.nonEmpty && chunk.checksum.hash.nonEmpty)
      log.debug("Chunk write success: {}", chunk)
      chunks.registerChunk(sender(), chunk)

    case WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failed: {}", chunk)
      chunks.unregisterChunk(sender(), chunk)
      chunks.retryPendingChunks()

    case Register(index) ⇒
      val dispatcher = sender()
      if (log.isDebugEnabled) log.debug("Registered storage {} with {} entries", dispatcher, index.chunks.size)
      storages.register(dispatcher)
      chunks.register(dispatcher, index)
      chunks.retryPendingChunks()

    case Update(diff) ⇒
      val dispatcher = sender()
      if (log.isDebugEnabled) log.debug("Storage index updated {}: {}", dispatcher, diff)
      require(storages.contains(dispatcher))
      chunks.update(dispatcher, diff)

    case Terminated(dispatcher) ⇒
      log.debug("Watched actor terminated: {}", dispatcher)
      storages.unregister(dispatcher)
      chunks.unregister(dispatcher)
  }
}
