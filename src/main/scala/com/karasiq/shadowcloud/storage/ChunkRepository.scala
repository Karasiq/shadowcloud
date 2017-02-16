package com.karasiq.shadowcloud.storage

import java.nio.file.{Files, Path}

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileChunkRepository
import com.karasiq.shadowcloud.storage.inmem.InMemoryChunkRepository
import com.karasiq.shadowcloud.storage.wrappers.{ByteStringChunkRepository, HexStringChunkRepositoryWrapper}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

trait ChunkRepository[ChunkKey] {
  def chunks: Source[ChunkKey, _]
  def read(key: ChunkKey): Source[ByteString, _]
  def write(key: ChunkKey): Sink[ByteString, _]
}

trait BaseChunkRepository extends ChunkRepository[String]

object ChunkRepository {
  def inMemory: BaseChunkRepository = {
    new InMemoryChunkRepository
  }

  def fromDirectory(directory: Path)(implicit ec: ExecutionContext): BaseChunkRepository = {
    require(Files.isDirectory(directory), s"Not directory: $directory")
    new FileChunkRepository(directory)
  }

  def hexString(underlying: BaseChunkRepository): ByteStringChunkRepository = {
    new HexStringChunkRepositoryWrapper(underlying)
  }
}