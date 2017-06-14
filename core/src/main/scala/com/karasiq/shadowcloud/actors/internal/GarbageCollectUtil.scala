package com.karasiq.shadowcloud.actors.internal

import akka.util.ByteString

import com.karasiq.shadowcloud.actors.utils.GCState
import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.storage.utils.IndexMerger

private[actors] object GarbageCollectUtil {
  def apply(config: StorageConfig): GarbageCollectUtil = {
    new GarbageCollectUtil(config)
  }
}

private[actors] final class GarbageCollectUtil(config: StorageConfig) {
  def collect(index: IndexMerger[_], storageChunks: Set[ByteString]): GCState = {
    val indexPersistedChunks = index.chunks
    val indexPendingChunks = index.chunks.patch(index.pending.chunks)
    val indexPendingFolders = index.folders.patch(index.pending.folders)

    GCState(
      orphanedChunks(indexPersistedChunks, indexPendingFolders),
      notIndexedChunks(indexPendingChunks, storageChunks),
      notExistingChunks(indexPersistedChunks, storageChunks)
    )
  }

  private[this] def orphanedChunks(chunkIndex: ChunkIndex, folderIndex: FolderIndex): Set[Chunk] = {
    val fileChunks = (for {
      (_, folder) ← folderIndex.folders
      file ← folder.files
      chunk ← file.chunks
    } yield chunk).toSet
    chunkIndex.chunks.diff(fileChunks)
  }

  private[this] def notIndexedChunks(chunks: ChunkIndex, keys: Set[ByteString]): Set[ByteString] = {
    val existing = chunks.chunks.map(config.chunkKey)
    keys.diff(existing)
  }

  private[this] def notExistingChunks(chunks: ChunkIndex, keys: Set[ByteString]): Set[Chunk] = {
    chunks.chunks.filterNot(c ⇒ keys.contains(config.chunkKey(c)))
  }
}
