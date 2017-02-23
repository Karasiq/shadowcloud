package com.karasiq.shadowcloud.actors.internal

import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.index.utils.HasEmpty

import scala.language.postfixOps

private[actors] case class DiffStats(diffs: Int, files: Int, folders: Int, chunks: Int,
                                     fileDeletes: Int, folderDeletes: Int, chunkDeletes: Int) extends HasEmpty {
  require(diffs >= 0 && files >= 0 && folders >= 0 && chunks >= 0 &&
    fileDeletes >= 0 && folderDeletes >= 0 && chunkDeletes >= 0)
  
  def +(stat: DiffStats): DiffStats = {
    copy(diffs + stat.diffs, files + stat.files, folders + stat.folders, chunks + stat.chunks, fileDeletes + stat.fileDeletes,
      folderDeletes + stat.folderDeletes, chunkDeletes + stat.chunkDeletes)
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
    s"DiffStats(files = $files, folders = $folders, chunks = $chunks, deletes: $deletes)"
  }
}

private[actors] object DiffStats {
  val empty = DiffStats(0, 0, 0, 0, 0, 0, 0)

  def apply(diffs: IndexDiff*): DiffStats = {
    if (diffs.isEmpty) return empty
    var diffCount, files, folders, chunks, fileDeletes, folderDeletes, chunkDeletes = 0
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
    }
    DiffStats(diffCount, files, folders, chunks, fileDeletes, folderDeletes, chunkDeletes)
  }
}
