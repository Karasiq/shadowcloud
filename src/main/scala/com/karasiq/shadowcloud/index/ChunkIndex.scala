package com.karasiq.shadowcloud.index

import akka.util.ByteString

import scala.language.postfixOps

case class ChunkIndex(chunks: Map[ByteString, Chunk] = Map.empty) {
  def merge(second: ChunkIndex): ChunkIndex = {
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
}
