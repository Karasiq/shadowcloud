package com.karasiq.shadowcloud.index.diffs

import scala.language.postfixOps

import com.karasiq.shadowcloud.index.utils._
import com.karasiq.shadowcloud.model.{Chunk, Folder, Path, SCEntity}
import com.karasiq.shadowcloud.utils.Utils
import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}

@SerialVersionUID(0L)
final case class IndexDiff(time: Long = Utils.timestamp,
                           folders: FolderIndexDiff = FolderIndexDiff.empty,
                           chunks: ChunkIndexDiff = ChunkIndexDiff.empty)
  extends SCEntity with MergeableDiff with HasEmpty with HasWithoutData {

  type Repr = IndexDiff

  // Delete wins by default
  def mergeWith(diff: IndexDiff, folderDiffDecider: FolderDiffDecider = FolderDiffDecider.rightWins,
                folderDecider: FolderDecider = FolderDecider.mutualExclude,
                chunkDecider: SplitDecider[Chunk] = SplitDecider.dropDuplicates): IndexDiff = {
    val maxTime = math.max(time, diff.time)
    withDiffs(folders.mergeWith(diff.folders, folderDiffDecider, folderDecider),
      chunks.mergeWith(diff.chunks, chunkDecider), maxTime)
  }

  def diffWith(diff: IndexDiff, decider: Decider[FolderDiff] = Decider.diff,
               folderDiffDecider: FolderDiffDecider = FolderDiffDecider.idempotent,
               folderDecider: FolderDecider = FolderDecider.mutualExclude,
               chunkDecider: Decider[Chunk] = Decider.diff): IndexDiff = {
    withDiffs(folders.diffWith(diff.folders, decider, folderDiffDecider, folderDecider),
      chunks.diffWith(diff.chunks, chunkDecider))
  }

  def merge(right: IndexDiff): IndexDiff = {
    mergeWith(right)
  }

  def diff(right: IndexDiff): IndexDiff = {
    diffWith(right)
  }

  def creates: IndexDiff = {
    withDiffs(folders.creates, chunks.creates)
  }

  def deletes: IndexDiff = {
    withDiffs(folders.deletes, chunks.deletes)
  }

  def reverse: IndexDiff = {
    withDiffs(folders.reverse, chunks.reverse)
  }

  def withoutData: IndexDiff = {
    withDiffs(folders.withoutData, chunks.withoutData)
  }

  def isEmpty: Boolean = {
    folders.isEmpty && chunks.isEmpty
  }

  override def toString: String = {
    s"IndexDiff($time, $folders, $chunks)"
  }

  private[this] def withDiffs(folders: FolderIndexDiff = this.folders,
                              chunks: ChunkIndexDiff = this.chunks,
                              time: Long = this.time): IndexDiff = {
    if (folders.isEmpty && chunks.isEmpty) IndexDiff.empty else copy(time, folders, chunks)
  }
}

object IndexDiff {
  val empty = IndexDiff(0L)

  def newChunks(chunks: Chunk*): IndexDiff = {
    if (chunks.isEmpty) {
      empty
    } else {
      IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff.create(chunks: _*))
    }
  }

  def deleteChunks(chunks: Chunk*): IndexDiff = {
    if (chunks.isEmpty) {
      empty
    } else {
      IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff.delete(chunks: _*))
    }
  }

  def newFolders(folders: Folder*): IndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      IndexDiff(folders.map(_.timestamp.lastModified).max, FolderIndexDiff.createFolders(folders: _*))
    }
  }

  def deleteFolders(folders: Path*): IndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      IndexDiff(Utils.timestamp, FolderIndexDiff.deleteFolderPaths(folders: _*))
    }
  }
}
