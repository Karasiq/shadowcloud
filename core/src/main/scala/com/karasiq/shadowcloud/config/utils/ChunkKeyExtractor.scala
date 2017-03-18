package com.karasiq.shadowcloud.config.utils

import akka.util.ByteString
import com.karasiq.shadowcloud.index.Chunk

import scala.language.postfixOps

private[shadowcloud] trait ChunkKeyExtractor extends (Chunk ⇒ ByteString) {
  def apply(chunk: Chunk): ByteString
}

private[shadowcloud] object ChunkKeyExtractor {
  val hash: ChunkKeyExtractor = (chunk: Chunk) ⇒ chunk.checksum.hash
  val encryptedHash: ChunkKeyExtractor = (chunk: Chunk) ⇒ chunk.checksum.encryptedHash
  val doubleHash: ChunkKeyExtractor = (chunk: Chunk) ⇒ chunk.checksum.hash ++ chunk.checksum.encryptedHash

  def fromString(str: String): ChunkKeyExtractor = str match {
    case "hash" ⇒
      hash

    case "encrypted-hash" ⇒
      encryptedHash

    case "double-hash" ⇒
      doubleHash

    case _ ⇒
      Class.forName(str)
        .asSubclass(classOf[ChunkKeyExtractor])
        .newInstance()
  }
}