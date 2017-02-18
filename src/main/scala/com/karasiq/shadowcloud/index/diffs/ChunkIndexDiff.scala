package com.karasiq.shadowcloud.index.diffs

import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData, MergeableDiff}
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndex}
import com.karasiq.shadowcloud.utils.MergeUtil.{Decider, SplitDecider}
import com.karasiq.shadowcloud.utils.{MergeUtil, Utils}

import scala.language.postfixOps

case class ChunkIndexDiff(newChunks: Set[Chunk] = Set.empty, deletedChunks: Set[Chunk] = Set.empty)
  extends MergeableDiff[ChunkIndexDiff] with HasEmpty with HasWithoutData[ChunkIndexDiff] {

  // Delete wins by default
  def mergeWith(diff: ChunkIndexDiff, decider: SplitDecider[Chunk] = SplitDecider.dropDuplicates): ChunkIndexDiff = {
    val (newChunks, deletedChunks) = MergeUtil.splitSets(this.newChunks ++ diff.newChunks,
      this.deletedChunks ++ diff.deletedChunks, decider)
    withChunks(newChunks, deletedChunks)
  }

  def diffWith(diff: ChunkIndexDiff, decider: Decider[Chunk] = Decider.diff): ChunkIndexDiff = {
    withChunks(MergeUtil.mergeSets(this.newChunks, diff.newChunks, decider),
      MergeUtil.mergeSets(this.deletedChunks, diff.deletedChunks, decider))
  }

  def merge(right: ChunkIndexDiff): ChunkIndexDiff = {
    mergeWith(right)
  }

  def diff(right: ChunkIndexDiff): ChunkIndexDiff = {
    diffWith(right)
  }

  def reverse: ChunkIndexDiff = {
    withChunks(deletedChunks, newChunks)
  }

  def deletes: ChunkIndexDiff = {
    withChunks(newChunks = Set.empty)
  }
  
  def creates: ChunkIndexDiff = {
    withChunks(deletedChunks = Set.empty)
  }

  def withoutData: ChunkIndexDiff = {
    withChunks(newChunks.map(_.withoutData), deletedChunks.map(_.withoutData))
  }

  def isEmpty: Boolean = {
    newChunks.isEmpty && deletedChunks.isEmpty
  }

  override def toString: String = {
    if (nonEmpty) {
      s"ChunkIndexDiff(new = [${Utils.printHashes(newChunks)}], deleted = [${Utils.printHashes(deletedChunks)}])"
    } else {
      "ChunkIndexDiff.empty"
    }
  }

  private[this] def withChunks(newChunks: Set[Chunk] = this.newChunks, deletedChunks: Set[Chunk] = this.deletedChunks): ChunkIndexDiff = {
    if (newChunks.isEmpty && deletedChunks.isEmpty) {
      ChunkIndexDiff.empty
    } else {
      copy(newChunks, deletedChunks)
    }
  }
}

object ChunkIndexDiff {
  val empty = ChunkIndexDiff()

  def apply(oldIndex: ChunkIndex, newIndex: ChunkIndex): ChunkIndexDiff = {
    val (oldOnly, newOnly) = MergeUtil.splitSets(oldIndex.chunks, newIndex.chunks, SplitDecider.dropDuplicates)
    if (oldOnly.isEmpty && newOnly.isEmpty) {
      empty
    } else {
      ChunkIndexDiff(newChunks = newOnly, deletedChunks = oldOnly)
    }
  }

  def wrap(chunkIndex: ChunkIndex): ChunkIndexDiff = {
    ChunkIndexDiff(newChunks = chunkIndex.chunks)
  }

  def create(chunks: Chunk*): ChunkIndexDiff = {
    if (chunks.isEmpty) empty else ChunkIndexDiff(newChunks = chunks.toSet)
  }

  def delete(chunks: Chunk*): ChunkIndexDiff = {
    if (chunks.isEmpty) empty else ChunkIndexDiff(deletedChunks = chunks.toSet)
  }
}
