package com.karasiq.shadowcloud.storage.replication

import com.karasiq.shadowcloud.index.utils.HasEmpty

case class ChunkAvailability(hasChunk: Set[String] = Set.empty,
                             writingChunk: Set[String] = Set.empty,
                             writeFailed: Set[String] = Set.empty,
                             readFailed: Set[String] = Set.empty) extends HasEmpty {

  def contains(storageId: String): Boolean = {
    hasChunk.contains(storageId) || writingChunk.contains(storageId) ||
      writeFailed.contains(storageId) || readFailed.contains(storageId)
  }

  def isEmpty: Boolean = {
    hasChunk.isEmpty && writingChunk.isEmpty
  }

  def isWritten(storageId: String): Boolean = {
    hasChunk.contains(storageId)
  }

  def isWriting(storageId: String): Boolean = {
    hasChunk.contains(storageId) || writingChunk.contains(storageId)
  }

  def isFailed(storageId: String): Boolean = {
    writeFailed.contains(storageId) || readFailed.contains(storageId)
  }

  def withWriting(storageIds: String*): ChunkAvailability = {
    copy(hasChunk = hasChunk -- storageIds, writingChunk = writingChunk ++ storageIds, writeFailed = writeFailed -- storageIds)
  }

  def withFinished(storageIds: String*): ChunkAvailability = {
    copy(hasChunk = hasChunk ++ storageIds, writingChunk = writingChunk -- storageIds)
  }

  def withWriteFailed(storageIds: String*): ChunkAvailability = {
    copy(writingChunk = writingChunk -- storageIds, writeFailed = writeFailed ++ storageIds)
  }

  def withReadFailed(storageIds: String*): ChunkAvailability = {
    copy(readFailed = readFailed ++ storageIds)
  }

  def -(storageId: String): ChunkAvailability = {
    copy(hasChunk - storageId, writingChunk - storageId, writeFailed - storageId)
  }
}

object ChunkAvailability {
  val empty = ChunkAvailability()
}