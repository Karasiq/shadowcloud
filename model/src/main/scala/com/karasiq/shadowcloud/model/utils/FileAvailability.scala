package com.karasiq.shadowcloud.model.utils

import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.model.{Chunk, File, StorageId}

@SerialVersionUID(0L)
final case class FileAvailability(file: File, chunksByStorage: Map[StorageId, Set[Chunk]]) extends HasEmpty {
  override def isEmpty: Boolean = chunksByStorage.isEmpty

  def percentagesByStorage: Map[StorageId, Double] = {
    val totalChunks = file.chunks.length
    chunksByStorage.mapValues { storageChunks ⇒
      storageChunks.size.toDouble / totalChunks * 100
    }
  }

  def isFullyAvailable: Boolean = {
    val fileChunks = file.chunks.toSet
    val actualChunks = chunksByStorage.flatMap(_._2).toSet
    fileChunks == actualChunks
  }
}
