package com.karasiq.shadowcloud.storage.utils

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.index.Chunk

private[shadowcloud] trait ChunkKeyMapper extends (Chunk ⇒ ByteString) {
  def apply(chunk: Chunk): ByteString
}

private[shadowcloud] object ChunkKeyMapper {
  val hash: ChunkKeyMapper = (chunk: Chunk) ⇒ chunk.checksum.hash
  val encryptedHash: ChunkKeyMapper = (chunk: Chunk) ⇒ chunk.checksum.encHash
  val doubleHash: ChunkKeyMapper = (chunk: Chunk) ⇒ chunk.checksum.hash ++ chunk.checksum.encHash
}