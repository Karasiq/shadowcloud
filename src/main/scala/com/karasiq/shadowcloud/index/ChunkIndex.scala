package com.karasiq.shadowcloud.index

import akka.util.ByteString
import org.apache.commons.codec.binary.Hex

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

case class ChunkIndex(chunks: Map[ByteString, Chunk] = Map.empty) {
  def contains(hash: ByteString) = {
    chunks.contains(hash)
  }

  def addChunks(chunks: GenTraversableOnce[Chunk]): ChunkIndex = {
    val newChunks = chunks.toIterator.map { chunk ⇒
      val existing = this.chunks.get(chunk.hash)
      require(existing.isEmpty || existing.contains(chunk), s"Conflict: ${existing.mkString} / $chunk")
      (chunk.hash, existing.getOrElse(chunk))
    }
    ChunkIndex(this.chunks ++ newChunks)
  }

  def addChunks(chunks: Chunk*): ChunkIndex = {
    addChunks(chunks)
  }

  def deleteChunks(hashes: GenTraversableOnce[ByteString]): ChunkIndex = {
    copy(this.chunks -- hashes)
  }

  def deleteChunks(hashes: ByteString*): ChunkIndex = {
    deleteChunks(hashes)
  }

  def merge(second: ChunkIndex) = {
    addChunks(second.chunks.values)
  }

  def diff(second: ChunkIndex) = {
    deleteChunks(second.chunks.keys)
  }

  override def toString = {
    val hashesStr = chunks.keys.take(20).map(hash ⇒ Hex.encodeHexString(hash.toArray)).mkString(", ")
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
