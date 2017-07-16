package com.karasiq.shadowcloud.storage.replication

import com.karasiq.shadowcloud.storage.replication.ChunkStatusProvider.ChunkStatus

case class ChunkWriteAffinity(mandatory: Seq[String] = Vector.empty,
                              eventually: Seq[String] = Vector.empty, // Can hold chunk in memory forever
                              optional: Seq[String] = Vector.empty) {
  def selectForWrite(cs: ChunkStatus): Seq[String] = {
    (mandatory ++ eventually).filterNot(cs.availability.isWriting) ++
      optional.filterNot(stId ⇒ cs.availability.isWriting(stId) || cs.availability.isFailed(stId))
  }

  def isWrittenEnough(cs: ChunkStatus): Boolean = {
    mandatory.forall(cs.availability.isWritten)
  }

  def isFinished(cs: ChunkStatus): Boolean = {
    (mandatory ++ eventually).forall(cs.availability.isWritten) // && optional.forall(cs.writeStatus.isWriting)
  }
}

object ChunkWriteAffinity {
  val empty = ChunkWriteAffinity()
}
