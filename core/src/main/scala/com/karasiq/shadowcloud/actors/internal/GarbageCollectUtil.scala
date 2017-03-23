package com.karasiq.shadowcloud.actors.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.utils.Utils

private[actors] object GarbageCollectUtil {
  def apply(config: StorageConfig): GarbageCollectUtil = {
    new GarbageCollectUtil(config)
  }

  case class State(orphaned: Set[Chunk], notIndexed: Set[ByteString], notExisting: Set[Chunk]) extends HasEmpty {
    def isEmpty: Boolean = orphaned.isEmpty && notIndexed.isEmpty && notExisting.isEmpty

    override def toString: String = {
      s"GarbageCollectUtil.State(orphaned = [${Utils.printChunkHashes(orphaned)}], not indexed = [${Utils.printHashes(notIndexed)}], not existing = [${Utils.printChunkHashes(notExisting)}])"
    }
  }
}

private[actors] final class GarbageCollectUtil(config: StorageConfig) {
  def collect(index: IndexMerger[_], storageChunks: Set[ByteString]): GarbageCollectUtil.State = {
    val indexPersistedChunks = index.chunks
    val indexPendingChunks = index.chunks.patch(index.pending.chunks)
    val indexPendingFolders = index.folders.patch(index.pending.folders)

    GarbageCollectUtil.State(
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
