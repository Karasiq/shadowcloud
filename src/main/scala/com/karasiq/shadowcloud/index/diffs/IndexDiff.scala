package com.karasiq.shadowcloud.index.diffs

import com.karasiq.shadowcloud.index._
import com.karasiq.shadowcloud.index.utils.FolderDecider
import com.karasiq.shadowcloud.utils.MergeUtil.State._
import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}
import com.karasiq.shadowcloud.utils.{MergeUtil, Utils}

import scala.language.postfixOps

case class IndexDiff(time: Long = 0, folders: Seq[FolderDiff] = Vector.empty, chunks: ChunkIndexDiff = ChunkIndexDiff.empty) {
  def nonEmpty: Boolean = {
    (folders.nonEmpty && folders.forall(_.nonEmpty)) || chunks.nonEmpty
  }

  // Delete wins by default
  def merge(diff: IndexDiff, folderDecider: FolderDecider = FolderDecider.mutualExclude,
            chunkDecider: SplitDecider[Chunk] = SplitDecider.dropDuplicates): IndexDiff = {
    val (first, second) = if (this.time > diff.time) (diff, this) else (this, diff)
    val folders = MergeUtil.mergeByKey[Path, FolderDiff](first.folders, second.folders, _.path, {
      case Conflict(left, right) ⇒
        Some(left.merge(right, folderDecider)).filter(_.nonEmpty)
    })
    IndexDiff.instanceOrEmpty(copy(second.time, folders, first.chunks.merge(second.chunks, chunkDecider)))
  }

  def diff(diff: IndexDiff, decider: Decider[FolderDiff] = Decider.diff,
           folderDecider: FolderDecider = FolderDecider.mutualExclude,
           chunkDecider: Decider[Chunk] = Decider.diff): IndexDiff = {
    val decider1: Decider[FolderDiff] = decider.orElse {
      case Conflict(left, right) ⇒
        Some(right.diff(left, folderDecider)).filter(_.nonEmpty)
    }
    val folders = MergeUtil.mergeByKey[Path, FolderDiff](this.folders, diff.folders, _.path, decider1)
    IndexDiff.instanceOrEmpty(copy(time, folders, chunks.diff(diff.chunks, chunkDecider)))
  }

  def reverse: IndexDiff = {
    IndexDiff.instanceOrEmpty(copy(time, folders.map(_.reverse), chunks.reverse))
  }

  def withoutData: IndexDiff = {
    IndexDiff.instanceOrEmpty(copy(time, folders.map(_.withoutData), chunks.withoutData))
  }

  override def toString: String = {
    s"IndexDiff($time, [${folders.mkString(", ")}], $chunks)"
  }
}

object IndexDiff {
  val empty = IndexDiff()

  def newChunks(chunks: Chunk*): IndexDiff = {
    if (chunks.isEmpty) empty else IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff(newChunks = chunks.toSet))
  }

  def deleteChunks(chunks: Chunk*): IndexDiff = {
    if (chunks.isEmpty) empty else IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff(deletedChunks = chunks.toSet))
  }

  def newFolders(folders: Folder*): IndexDiff = {
    if (folders.isEmpty) empty else IndexDiff(folders.map(_.lastModified).max, folders.map(FolderDiff.wrap))
  }

  def deleteFolders(folders: Path*): IndexDiff = {
    if (folders.isEmpty) {
      empty
    } else {
      val diffs = folders
        .filterNot(_.isRoot)
        .groupBy(_.parent)
        .map { case (parent, folders) ⇒
          FolderDiff(parent, Utils.timestamp, deletedFolders = folders.map(_.name).toSet)
        }
      IndexDiff(Utils.timestamp, diffs.toVector)
    }
  }

  @inline
  private def instanceOrEmpty(diff: IndexDiff): IndexDiff = {
    if (diff.nonEmpty) diff else empty
  }
}
