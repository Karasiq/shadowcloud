package com.karasiq.shadowcloud.index

import scala.language.postfixOps

case class ChunkIndexDiff(newChunks: Set[Chunk] = Set.empty, deletedChunks: Set[Chunk] = Set.empty) {
  def merge(diff: ChunkIndexDiff): ChunkIndexDiff = {
    copy(
      newChunks -- diff.deletedChunks ++ diff.newChunks,
      deletedChunks -- diff.newChunks ++ diff.deletedChunks
    )
  }
}

object ChunkIndexDiff {
  val empty = ChunkIndexDiff()

  def apply(index1: ChunkIndex, index2: ChunkIndex): ChunkIndexDiff = {
    ChunkIndexDiff(
      index2.chunks.diff(index1.chunks),
      index1.chunks.diff(index2.chunks)
    )
  }
}
