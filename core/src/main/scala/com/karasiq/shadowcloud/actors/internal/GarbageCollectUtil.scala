package com.karasiq.shadowcloud.actors.internal

import akka.util.ByteString

import com.karasiq.shadowcloud.actors.utils.{RegionGCState, StorageGCState}
import com.karasiq.shadowcloud.config.{GCConfig, StorageConfig}
import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.metadata.MetadataUtils
import com.karasiq.shadowcloud.model.{Chunk, File, FileId, Timestamp}
import com.karasiq.shadowcloud.storage.utils.IndexMerger
import com.karasiq.shadowcloud.utils.Utils

private[actors] object GarbageCollectUtil {
  def apply(config: GCConfig): GarbageCollectUtil = {
    new GarbageCollectUtil(config)
  }
}

private[actors] final class GarbageCollectUtil(config: GCConfig) {
  def checkStorage(index: IndexMerger[_], storageConfig: StorageConfig, storageChunks: Set[ByteString]): StorageGCState = {
    val indexPersistedChunks = index.chunks
    val indexPendingChunks = index.chunks.patch(index.pending.chunks)

    StorageGCState(
      notIndexedChunks(indexPendingChunks, storageConfig, storageChunks),
      notExistingChunks(indexPersistedChunks, storageConfig, storageChunks)
    )
  }

  def checkRegion(index: IndexMerger[_]): RegionGCState = {
    val indexPersistedChunks = index.chunks
    val indexPendingFolders = index.folders.patch(index.pending.folders)

    RegionGCState(
      orphanedChunks(indexPersistedChunks, indexPendingFolders),
      oldRevisions(indexPendingFolders),
      expiredMetadata(indexPendingFolders)
    )
  }

  private[this] def expiredMetadata(index: FolderIndex): Set[FileId] = {
    MetadataUtils.expiredFileIds(index)
  }

  private[this] def oldRevisions(index: FolderIndex): Set[File] = {
    val filesByPath = index.folders
      .flatMap(_._2.files)
      .groupBy(_.path)

    val filesToDelete = filesByPath.flatMap { case (_, files) ⇒
      def isFileRecent(file: File): Boolean = {
        val now = Utils.timestamp
        val retention = config.keepRecentFiles.toMillis
        (file.timestamp.lastModified + retention) >= now
      }

      val recentFiles = files.filter(isFileRecent)
      val lastRevisions = files.toSeq
        .sortBy(f ⇒ (f.revision, f.timestamp))(Ordering[(Long, Timestamp)].reverse)
        .take(math.max(1, config.keepFileRevisions))

      files.toSet -- recentFiles -- lastRevisions
    }

    filesToDelete.toSet
  }

  private[this] def orphanedChunks(chunkIndex: ChunkIndex, folderIndex: FolderIndex): Set[Chunk] = {
    val fileChunks = (for {
      (_, folder) ← folderIndex.folders
      file ← folder.files
      chunk ← file.chunks
    } yield chunk).toSet
    chunkIndex.chunks.diff(fileChunks)
  }

  private[this] def notIndexedChunks(chunks: ChunkIndex, storageConfig: StorageConfig, keys: Set[ByteString]): Set[ByteString] = {
    val existing = chunks.chunks.map(storageConfig.chunkKey)
    keys.diff(existing)
  }

  private[this] def notExistingChunks(chunks: ChunkIndex, storageConfig: StorageConfig, keys: Set[ByteString]): Set[Chunk] = {
    chunks.chunks.filterNot(c ⇒ keys.contains(storageConfig.chunkKey(c)))
  }
}
