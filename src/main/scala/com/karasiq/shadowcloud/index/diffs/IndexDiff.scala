package com.karasiq.shadowcloud.index.diffs

import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.utils.{FolderDecider, HasEmpty, HasWithoutData, MergeableDiff}
import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

case class IndexDiff(time: Long = 0, folders: FolderIndexDiff = FolderIndexDiff.empty,
                     chunks: ChunkIndexDiff = ChunkIndexDiff.empty)
  extends MergeableDiff with HasEmpty with HasWithoutData {

  type Repr = IndexDiff

  // Delete wins by default
  def mergeWith(diff: IndexDiff, folderDecider: FolderDecider = FolderDecider.mutualExclude,
            chunkDecider: SplitDecider[Chunk] = SplitDecider.dropDuplicates): IndexDiff = {
    withDiffs(math.max(time, diff.time), folders.mergeWith(diff.folders, folderDecider), chunks.mergeWith(diff.chunks, chunkDecider))
  }

  def diffWith(diff: IndexDiff, decider: Decider[FolderDiff] = Decider.diff,
           folderDecider: FolderDecider = FolderDecider.mutualExclude,
           chunkDecider: Decider[Chunk] = Decider.diff): IndexDiff = {
    withDiffs(time, folders.diffWith(diff.folders, decider, folderDecider), chunks.diffWith(diff.chunks, chunkDecider))
  }

  def merge(right: IndexDiff): IndexDiff = {
    mergeWith(right)
  }

  def diff(right: IndexDiff): IndexDiff = {
    diffWith(right)
  }

  def creates: IndexDiff = {
    withDiffs(time, folders.creates, chunks.creates)
  }

  def deletes: IndexDiff = {
    withDiffs(time, folders.deletes, chunks.deletes)
  }

  def reverse: IndexDiff = {
    withDiffs(time, folders.reverse, chunks.reverse)
  }

  def withoutData: IndexDiff = {
    withDiffs(time, folders.withoutData, chunks.withoutData)
  }

  def isEmpty: Boolean = {
    folders.isEmpty && chunks.isEmpty
  }

  override def toString: String = {
    s"IndexDiff($time, $folders, $chunks)"
  }

  private[this] def withDiffs(time: Long = this.time, folders: FolderIndexDiff = this.folders, chunks: ChunkIndexDiff = this.chunks): IndexDiff = {
    if (folders.isEmpty && chunks.isEmpty) IndexDiff.empty else copy(time, folders, chunks)
  }
}

object IndexDiff {
  val empty = IndexDiff()

  def newChunks(chunks: Chunk*): IndexDiff = {
    if (chunks.isEmpty) {
      empty
    } else {
      IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff.create(chunks:_*))
    }
  }

  def deleteChunks(chunks: Chunk*): IndexDiff = {
    if (chunks.isEmpty) {
      empty
    } else {
      IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff.delete(chunks:_*))
    }
  }

  def newFolders(folders: Folder*): IndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      IndexDiff(folders.map(_.lastModified).max, FolderIndexDiff.create(folders:_*))
    }
  }

  def deleteFolders(folders: Path*): IndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      IndexDiff(Utils.timestamp, FolderIndexDiff.delete(folders:_*))
    }
  }
}
