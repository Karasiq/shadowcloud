package com.karasiq.shadowcloud.storage.replication

import com.karasiq.shadowcloud.index.utils.HasEmpty

case class ChunkAvailability(hasChunk: Set[String] = Set.empty,
                             writingChunk: Set[String] = Set.empty,
                             writeFailed: Set[String] = Set.empty) extends HasEmpty {
  def contains(storageId: String): Boolean = {
    hasChunk.contains(storageId) || writingChunk.contains(storageId) || writeFailed.contains(storageId)
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
    writeFailed.contains(storageId)
  }

  def finished(storageId: String): ChunkAvailability = {
    copy(hasChunk = hasChunk + storageId, writingChunk = writingChunk - storageId)
  }

  def failed(storageId: String): ChunkAvailability = {
    copy(writingChunk = writingChunk - storageId, writeFailed = writeFailed + storageId)
  }

  def -(storageId: String): ChunkAvailability = {
    copy(hasChunk - storageId, writingChunk - storageId, writeFailed - storageId)
  }
}

object ChunkAvailability {
  val empty = ChunkAvailability()
}