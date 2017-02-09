package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.MergeUtil
import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}

import scala.language.postfixOps

case class ChunkIndexDiff(newChunks: Set[Chunk] = Set.empty, deletedChunks: Set[Chunk] = Set.empty) {
  def nonEmpty: Boolean = {
    newChunks.nonEmpty || deletedChunks.nonEmpty
  }

  // Delete wins by default
  def merge(diff: ChunkIndexDiff, decider: SplitDecider[Chunk] = SplitDecider.dropDuplicates): ChunkIndexDiff = {
    val (newChunks, deletedChunks) = MergeUtil.splitSets(this.newChunks ++ diff.newChunks,
      this.deletedChunks ++ diff.deletedChunks, decider)
    ChunkIndexDiff.instanceOrEmpty(copy(newChunks, deletedChunks))
  }

  def diff(diff: ChunkIndexDiff, decider: Decider[Chunk] = Decider.diff): ChunkIndexDiff = {
    val newDiff = copy(
      MergeUtil.mergeSets(this.newChunks, diff.newChunks, decider),
      MergeUtil.mergeSets(this.deletedChunks, diff.deletedChunks, decider)
    )
    ChunkIndexDiff.instanceOrEmpty(newDiff)
  }

  def reverse: ChunkIndexDiff = {
    ChunkIndexDiff.instanceOrEmpty(copy(deletedChunks, newChunks))
  }
}

object ChunkIndexDiff {
  val empty = ChunkIndexDiff()

  def apply(first: ChunkIndex, second: ChunkIndex): ChunkIndexDiff = {
    val (firstOnly, secondOnly) = MergeUtil.splitSets(first.chunks, second.chunks, SplitDecider.dropDuplicates)
    ChunkIndexDiff(secondOnly, firstOnly)
  }

  @inline
  private def instanceOrEmpty(diff: ChunkIndexDiff): ChunkIndexDiff = {
    if (diff.nonEmpty) diff else empty
  }
}
