package com.karasiq.shadowcloud.storage.replication

import akka.actor.ActorRef

import com.karasiq.shadowcloud.model.Chunk
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus

object ChunkStatusProvider {
  sealed trait WriteStatus

  object WriteStatus {
    case class Pending(affinity: ChunkWriteAffinity) extends WriteStatus
    case object Finished extends WriteStatus
  }

  case class ChunkStatus(writeStatus: WriteStatus, chunk: Chunk,
                         availability: ChunkAvailability = ChunkAvailability.empty,
                         waitingChunk: Set[ActorRef] = Set.empty) {
    def finished: ChunkStatus = {
      copy(writeStatus = WriteStatus.Finished, chunk = chunk.withoutData)
    }
  }
}

trait ChunkStatusProvider {
  def getChunkStatus(chunk: Chunk): Option[ChunkStatus]
  def getChunkStatusList(): Iterable[ChunkStatus]
}
