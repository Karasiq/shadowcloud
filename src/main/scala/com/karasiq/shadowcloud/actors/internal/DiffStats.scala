package com.karasiq.shadowcloud.actors.internal

import com.karasiq.shadowcloud.index.diffs.IndexDiff

import scala.language.postfixOps

private[actors] case class DiffStats(files: Int, folders: Int, chunks: Int, fileDeletes: Int, folderDeletes: Int, chunkDeletes: Int) {
  def +(stat: DiffStats): DiffStats = {
    copy(files + stat.files, folders + stat.folders, chunks + stat.chunks, fileDeletes + stat.fileDeletes,
      folderDeletes + stat.folderDeletes, chunkDeletes + stat.chunkDeletes)
  }

  def creates: Int = {
    files + folders + chunks
  }

  def deletes: Int = {
    fileDeletes + folderDeletes + chunkDeletes
  }

  override def toString: String = {
    s"DiffStats(files = $files, folders = $folders, chunks = $chunks, deletes: $deletes)"
  }
}

private[actors] object DiffStats {
  val empty = DiffStats(0, 0, 0, 0, 0, 0)

  def apply(diff: IndexDiff): DiffStats = {
    var files, folders, fileDeletes, folderDeletes = 0
    diff.folders.folders.foreach { fd ⇒
      files += fd.newFiles.size
      folders += fd.newFolders.size
      fileDeletes += fd.deletedFiles.size
      folderDeletes += fd.deletedFolders.size
    }
    val chunks = diff.chunks.newChunks.size
    val chunkDeletes = diff.chunks.deletedChunks.size
    DiffStats(files, folders, chunks, fileDeletes, folderDeletes, chunkDeletes)
  }
}
