package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}
import com.karasiq.shadowcloud.utils.{MergeUtil, Utils}

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

  def deletes: ChunkIndexDiff = {
    ChunkIndexDiff.instanceOrEmpty(copy(newChunks = Set.empty))
  }
  
  def creates: ChunkIndexDiff = {
    ChunkIndexDiff.instanceOrEmpty(copy(deletedChunks = Set.empty))
  }

  override def toString: String = {
    if (nonEmpty) {
      s"ChunkIndexDiff(new = [${Utils.printHashes(newChunks)}], deleted = [${Utils.printHashes(deletedChunks)}])"
    } else {
      "ChunkIndexDiff.empty"
    }
  }
}

object ChunkIndexDiff {
  val empty = ChunkIndexDiff()

  def apply(oldIndex: ChunkIndex, newIndex: ChunkIndex): ChunkIndexDiff = {
    val (oldOnly, newOnly) = MergeUtil.splitSets(oldIndex.chunks, newIndex.chunks, SplitDecider.dropDuplicates)
    ChunkIndexDiff(newChunks = newOnly, deletedChunks = oldOnly)
  }

  @inline
  private def instanceOrEmpty(diff: ChunkIndexDiff): ChunkIndexDiff = {
    if (diff.nonEmpty) diff else empty
  }
}
