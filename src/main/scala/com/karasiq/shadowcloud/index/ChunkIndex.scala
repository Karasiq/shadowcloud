package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.index.diffs.ChunkIndexDiff
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

case class ChunkIndex(chunks: Set[Chunk] = Set.empty) {
  def contains(chunk: Chunk): Boolean = {
    chunks.contains(chunk)
  }

  def addChunks(chunks: GenTraversableOnce[Chunk]): ChunkIndex = {
    ChunkIndex(this.chunks ++ chunks)
  }

  def addChunks(chunks: Chunk*): ChunkIndex = {
    addChunks(chunks)
  }

  def deleteChunks(chunks: GenTraversableOnce[Chunk]): ChunkIndex = {
    copy(this.chunks -- chunks)
  }

  def deleteChunks(chunks: Chunk*): ChunkIndex = {
    deleteChunks(chunks)
  }

  def merge(second: ChunkIndex): ChunkIndex = {
    addChunks(second.chunks)
  }

  def diff(oldIndex: ChunkIndex): ChunkIndexDiff = {
    ChunkIndexDiff(this, oldIndex)
  }

  def patch(diff: ChunkIndexDiff): ChunkIndex = {
    deleteChunks(diff.deletedChunks).addChunks(diff.newChunks)
  }

  override def toString: String = {
    val hashesStr = chunks.take(20).map(chunk ⇒ Utils.toHexString(chunk.checksum.hash)).mkString(", ")
    val cutHashesStr = if (chunks.size > 20) hashesStr + ", ..." else hashesStr
    s"ChunkIndex($cutHashesStr)"
  }
}

object ChunkIndex {
  val empty = ChunkIndex()

  def apply(chunks: GenTraversableOnce[Chunk]): ChunkIndex = {
    ChunkIndex.empty.addChunks(chunks)
  }
}
