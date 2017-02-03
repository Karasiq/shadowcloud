package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.Utils

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

case class ChunkIndex(chunks: Set[Chunk] = Set.empty) {
  def contains(chunk: Chunk) = {
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

  def merge(second: ChunkIndex) = {
    addChunks(second.chunks)
  }

  def diff(second: ChunkIndex) = {
    ChunkIndexDiff(this, second)
  }

  def patch(diff: ChunkIndexDiff) = {
    deleteChunks(diff.deletedChunks).addChunks(diff.newChunks)
  }

  override def toString = {
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
