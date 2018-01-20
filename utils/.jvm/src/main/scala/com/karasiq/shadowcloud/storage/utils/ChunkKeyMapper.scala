package com.karasiq.shadowcloud.storage.utils

import scala.language.postfixOps

import akka.util.ByteString
import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits._
import com.karasiq.shadowcloud.model.{Chunk, ChunkId}
import com.karasiq.shadowcloud.utils.{ProviderInstantiator, Utils}

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
    case str ⇒
      config.optional(_.getConfig(str)) match {
        case Some(mapperConfig) ⇒
          ChunkKeyMapper.forConfig(mapperConfig)

        case None ⇒
          ProviderInstantiator.withConfig(str, config)
      }
  }

  def forConfig(config: Config): ChunkKeyMapper = {
    ProviderInstantiator.fromConfig[ChunkKeyMapper](config)
  }
}