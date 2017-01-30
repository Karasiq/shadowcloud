package com.karasiq.shadowcloud.index

import akka.util.ByteString
import org.apache.commons.codec.binary.Hex

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

case class ChunkIndex(chunks: Map[ByteString, Chunk] = Map.empty) {
  def merge(second: ChunkIndex) = {
    val newChunks = Map.newBuilder[ByteString, Chunk]
    newChunks ++= chunks
    second.chunks.foreach {
      case (hash, chunk) ⇒
        val existing = chunks.get(hash)
        require(existing.isEmpty || existing.contains(chunk), s"Conflict: ${existing.mkString} / $chunk")
        newChunks += hash → chunk
    }
    ChunkIndex(newChunks.result())
  }

  def contains(hash: ByteString) = {
    chunks.contains(hash)
  }

  def +(chunk: Chunk) = {
    copy(chunks + (chunk.hash → chunk))
  }

  def ++(chunks: GenTraversableOnce[Chunk]) = {
    val newMap = Map.newBuilder[ByteString, Chunk]
    newMap ++= this.chunks
    newMap ++= chunks.toIterator.map(chunk ⇒ chunk.hash → chunk)
    copy(newMap.result())
  }

  override def toString = {
    val hashesStr = chunks.keys.take(20).map(hash ⇒ Hex.encodeHexString(hash.toArray)).mkString(", ")
    val cutHashesStr = if (chunks.size > 20) hashesStr + ", ..." else hashesStr
    s"ChunkIndex($cutHashesStr)"
  }
}

object ChunkIndex {
  val empty = ChunkIndex()
}
