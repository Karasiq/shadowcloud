package com.karasiq.shadowcloud.index

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

import com.karasiq.shadowcloud.index.diffs.ChunkIndexDiff
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData, Mergeable}
import com.karasiq.shadowcloud.model.{Chunk, SCEntity}
import com.karasiq.shadowcloud.utils.Utils

@SerialVersionUID(0L)
final case class ChunkIndex(chunks: Set[Chunk] = Set.empty)
  extends SCEntity with Mergeable with HasEmpty with HasWithoutData {

  type Repr = ChunkIndex
  type DiffRepr = ChunkIndexDiff

  def contains(chunk: Chunk): Boolean = {
    chunks.contains(chunk)
  }

  def addChunks(chunks: GenTraversableOnce[Chunk]): ChunkIndex = {
    withChunks(this.chunks ++ chunks)
  }

  def addChunks(chunks: Chunk*): ChunkIndex = {
    addChunks(chunks)
  }

  def deleteChunks(chunks: GenTraversableOnce[Chunk]): ChunkIndex = {
    withChunks(this.chunks -- chunks)
  }

  def deleteChunks(chunks: Chunk*): ChunkIndex = {
    deleteChunks(chunks)
  }

  def merge(second: ChunkIndex): ChunkIndex = {
    addChunks(second.chunks)
  }

  def diff(oldIndex: ChunkIndex): ChunkIndexDiff = {
    ChunkIndexDiff(oldIndex, this)
  }

  def patch(diff: ChunkIndexDiff): ChunkIndex = {
    if (diff.isEmpty) this else withChunks(chunks ++ diff.newChunks -- diff.deletedChunks)
  }

  def isEmpty: Boolean = {
    chunks.isEmpty
  }

  def withoutData: ChunkIndex = {
    withChunks(chunks.map(_.withoutData))
  }

  override def toString: String = {
    s"ChunkIndex(${Utils.printChunkHashes(chunks)})"
  }

  private[this] def withChunks(chunks: Set[Chunk]): ChunkIndex = {
    if (chunks.isEmpty) ChunkIndex.empty else copy(chunks)
  }
}

object ChunkIndex {
  val empty = ChunkIndex()

  def apply(chunks: GenTraversableOnce[Chunk]): ChunkIndex = {
    ChunkIndex.empty.addChunks(chunks)
  }
}
