package com.karasiq.shadowcloud.actors.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.storage.utils.IndexMerger

private[actors] object GarbageCollector {
  def apply(config: StorageConfig, index: IndexMerger[_]): GarbageCollector = {
    new GarbageCollector(config, index)
  }
}

private[actors] final class GarbageCollector(config: StorageConfig, index: IndexMerger[_]) {
  def orphanedChunks: Set[Chunk] = {
    val fileChunks = (for {
      (_, folder) ← currentFolders.folders
      file ← folder.files
      chunk ← file.chunks
    } yield chunk).toSet
    currentChunks.chunks.diff(fileChunks)
  }

  def notIndexedChunks(keys: Set[ByteString]): Set[ByteString] = {
    val existing = currentChunks.chunks.map(config.chunkKey)
    keys.diff(existing)
  }

  private[this] def currentChunks: ChunkIndex = {
    index.chunks.patch(index.pending.chunks)
  }

  private[this] def currentFolders: FolderIndex = {
    index.folders.patch(index.pending.folders)
  }
}
