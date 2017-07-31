package com.karasiq.shadowcloud.utils

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.HasEmpty
import com.karasiq.shadowcloud.storage.utils.IndexMerger

case class DiffStats(diffs: Int, files: Int, folders: Int, chunks: Int,
                                     fileDeletes: Int, folderDeletes: Int, chunkDeletes: Int,
                                     createdBytes: Long, deletedBytes: Long) extends HasEmpty {
  require(diffs >= 0 && files >= 0 && folders >= 0 && chunks >= 0 &&
    fileDeletes >= 0 && folderDeletes >= 0 && chunkDeletes >= 0 && createdBytes >= 0 && deletedBytes >= 0)
  
  def +(stat: DiffStats): DiffStats = {
    DiffStats(diffs + stat.diffs, files + stat.files, folders + stat.folders, chunks + stat.chunks, fileDeletes + stat.fileDeletes,
      folderDeletes + stat.folderDeletes, chunkDeletes + stat.chunkDeletes, createdBytes + stat.createdBytes, deletedBytes + stat.deletedBytes)
  }

  def +(diff: IndexDiff): DiffStats = {
    this + DiffStats(diff)
  }

  def creates: Int = {
    files + folders + chunks
  }

  def deletes: Int = {
    fileDeletes + folderDeletes + chunkDeletes
  }

  def isEmpty: Boolean = {
    this == DiffStats.empty
  }

  override def toString: String = {
    s"DiffStats(files = $files, folders = $folders, chunks = $chunks, deletes: $deletes, created bytes: ${MemorySize.toString(createdBytes)}, deleted bytes: ${MemorySize.toString(deletedBytes)})"
  }
}

object DiffStats {
  val empty = DiffStats(0, 0, 0, 0, 0, 0, 0, 0, 0)

  def apply(diffs: IndexDiff*): DiffStats = {
    if (diffs.isEmpty) return empty
    var diffCount, files, folders, chunks, fileDeletes, folderDeletes, chunkDeletes = 0
    var createdBytes, deletedBytes = 0L
    diffs.foreach { diff ⇒
      diffCount += 1
      diff.folders.folders.foreach { fd ⇒
        files += fd.newFiles.size
        folders += fd.newFolders.size
        fileDeletes += fd.deletedFiles.size
        folderDeletes += fd.deletedFolders.size
      }
      chunks += diff.chunks.newChunks.size
      chunkDeletes += diff.chunks.deletedChunks.size
      createdBytes += diff.chunks.newChunks.map(_.checksum.size).sum
      deletedBytes += diff.chunks.deletedChunks.map(_.checksum.size).sum
    }
    DiffStats(diffCount, files, folders, chunks, fileDeletes, folderDeletes, chunkDeletes, createdBytes, deletedBytes)
  }

  def fromIndex(index: IndexMerger[_]): DiffStats = {
    apply(index.diffs.values.toSeq:_*)
  }
}
