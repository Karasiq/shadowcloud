package com.karasiq.shadowcloud.storage.replication

import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus

case class ChunkWriteAffinity(
    mandatory: Seq[StorageId] = Vector.empty,
    eventually: Seq[StorageId] = Vector.empty, // CAUTION: Can hold chunk in memory forever
    optional: Seq[StorageId] = Vector.empty
) extends HasEmpty {
  def all = mandatory ++ eventually ++ optional

  def isEmpty: Boolean = mandatory.isEmpty && eventually.isEmpty && optional.isEmpty

  def isFailed(cs: ChunkStatus): Boolean = {
    mandatory.forall(cs.availability.isFailed)
  }

  def selectForWrite(cs: ChunkStatus): Seq[StorageId] = {
    (mandatory ++ eventually).filterNot(cs.availability.isWriting) ++
      optional.filterNot(stId ⇒ cs.availability.isWriting(stId) || cs.availability.isFailed(stId))
  }

  def isWrittenEnough(cs: ChunkStatus): Boolean = {
    cs.availability.hasChunk.nonEmpty && mandatory.forall(cs.availability.isWritten)
  }

  def isFinished(cs: ChunkStatus): Boolean = {
    cs.availability.hasChunk.nonEmpty && (mandatory ++ eventually).forall(cs.availability.isWritten) // && optional.forall(cs.writeStatus.isWriting)
  }
}

object ChunkWriteAffinity {
  val empty = ChunkWriteAffinity()
}
