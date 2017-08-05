package com.karasiq.shadowcloud.utils

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.index.Chunk

private[shadowcloud] trait ChunkKeyExtractor extends (Chunk ⇒ ByteString) {
  def apply(chunk: Chunk): ByteString
}

private[shadowcloud] object ChunkKeyExtractor {
  val hash: ChunkKeyExtractor = (chunk: Chunk) ⇒ chunk.checksum.hash
  val encryptedHash: ChunkKeyExtractor = (chunk: Chunk) ⇒ chunk.checksum.encHash
  val doubleHash: ChunkKeyExtractor = (chunk: Chunk) ⇒ chunk.checksum.hash ++ chunk.checksum.encHash

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