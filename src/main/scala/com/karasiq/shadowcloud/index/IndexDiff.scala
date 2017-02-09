package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.MergeUtil
import com.karasiq.shadowcloud.utils.MergeUtil.State._
import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}

import scala.language.postfixOps

case class IndexDiff(time: Long = 0, folders: Seq[FolderDiff] = Nil, chunks: ChunkIndexDiff = ChunkIndexDiff.empty) {
  def nonEmpty: Boolean = {
    (folders.nonEmpty && folders.forall(_.nonEmpty)) || chunks.nonEmpty
  }

  // Delete wins by default
  def merge(diff: IndexDiff, filesDeciders: SplitDecider[File] = SplitDecider.keepRight, foldersDecider: SplitDecider[String] = SplitDecider.keepRight): IndexDiff = {
    val (first, second) = if (this.time > diff.time) (diff, this) else (this, diff)
    val folders = MergeUtil.mergeByKey[Path, FolderDiff](first.folders, second.folders, _.path, {
      case Conflict(left, right) ⇒
        Some(left.merge(right, filesDeciders, foldersDecider)).filter(_.nonEmpty)
    })
    IndexDiff.instanceOrEmpty(copy(second.time, folders, first.chunks.merge(second.chunks)))
  }

  def diff(diff: IndexDiff, decider: Decider[FolderDiff] = Decider.diff, filesDecider: Decider[File] = Decider.diff,
           foldersDecider: Decider[String] = Decider.diff, chunkDecider: Decider[Chunk] = Decider.diff): IndexDiff = {
    val decider1: Decider[FolderDiff] = decider.orElse {
      case Conflict(left, right) ⇒
        Some(left.diff(right, filesDecider, foldersDecider)).filter(_.nonEmpty)
    }
    val folders = MergeUtil.mergeByKey[Path, FolderDiff](this.folders, diff.folders, _.path, decider1)
    IndexDiff.instanceOrEmpty(copy(time, folders, chunks.diff(diff.chunks, chunkDecider)))
  }
}

object IndexDiff {
  val empty = IndexDiff()

  @inline
  private def instanceOrEmpty(diff: IndexDiff): IndexDiff = {
    if (diff.nonEmpty) diff else empty
  }
}
