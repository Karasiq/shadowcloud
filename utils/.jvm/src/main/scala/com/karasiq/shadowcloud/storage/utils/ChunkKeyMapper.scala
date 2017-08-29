package com.karasiq.shadowcloud.storage.utils

import scala.language.postfixOps
import scala.util.Try

import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.shadowcloud.model.{Chunk, ChunkId}
import com.karasiq.shadowcloud.utils.Utils

private[shadowcloud] trait ChunkKeyMapper extends (Chunk ⇒ ByteString) {
  def apply(chunk: Chunk): ChunkId
}

private[shadowcloud] object ChunkKeyMapper {
  val hash: ChunkKeyMapper = (chunk: Chunk) ⇒ chunk.checksum.hash
  val encryptedHash: ChunkKeyMapper = (chunk: Chunk) ⇒ chunk.checksum.encHash
  val doubleHash: ChunkKeyMapper = (chunk: Chunk) ⇒ chunk.checksum.hash ++ chunk.checksum.encHash

  def forName(name: String, config: Config = Utils.emptyConfig): ChunkKeyMapper = name match {
    // Predefined
    case "hash" ⇒ hash
    case "encrypted-hash" ⇒ encryptedHash
    case "double-hash" ⇒ doubleHash

    // Custom class
    case _ ⇒
      val mapperClass = Class.forName(name).asSubclass(classOf[ChunkKeyMapper])
      Try(mapperClass.getConstructor(classOf[Config]).newInstance(config))
        .orElse(Try(mapperClass.newInstance()))
        .getOrElse(throw new InstantiationException("No appropriate constructor found for " + mapperClass))
  }

  def forConfig(config: Config): ChunkKeyMapper = {
    forName(config.getString("name"), config)
  }
}