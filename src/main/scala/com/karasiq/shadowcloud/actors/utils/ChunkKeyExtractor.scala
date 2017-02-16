package com.karasiq.shadowcloud.actors.utils

import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk

import scala.language.postfixOps

trait ChunkKeyExtractor {
  def key(chunk: Chunk): ByteString
}

object ChunkKeyExtractor {
  val hash: ChunkKeyExtractor = (chunk: Chunk) ⇒ chunk.checksum.hash
}