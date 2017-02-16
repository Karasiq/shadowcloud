package com.karasiq.shadowcloud.storage

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.wrappers.{ByteStringChunkRepository, HexStringChunkRepositoryWrapper}

import scala.language.postfixOps

trait ChunkRepository[ChunkKey] {
  def chunks: Source[ChunkKey, _]
  def read(key: ChunkKey): Source[ByteString, _]
  def write(key: ChunkKey): Sink[ByteString, _]
}

trait BaseChunkRepository extends ChunkRepository[String]

object ChunkRepository {
  def hexString(underlying: BaseChunkRepository): ByteStringChunkRepository = {
    new HexStringChunkRepositoryWrapper(underlying)
  }
}