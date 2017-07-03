package com.karasiq.shadowcloud.storage.replication

import akka.actor.ActorRef

import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus

object ChunkStatusProvider {
  object Status extends Enumeration {
    val PENDING, STORED = Value
  }

  case class ChunkStatus(status: Status.Value, time: Long, chunk: Chunk, writingChunk: Set[String] = Set.empty,
                         hasChunk: Set[String] = Set.empty, waitingChunk: Set[ActorRef] = Set.empty)
}

trait ChunkStatusProvider {
  def getChunkStatus(chunk: Chunk): Option[ChunkStatus]
  def chunksStatus: Iterable[ChunkStatus]
}
