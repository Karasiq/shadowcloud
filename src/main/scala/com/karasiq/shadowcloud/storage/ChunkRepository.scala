package com.karasiq.shadowcloud.storage

import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileChunkRepository
import com.karasiq.shadowcloud.storage.inmem.InMemoryChunkRepository
import com.karasiq.shadowcloud.storage.wrappers.{ByteStringChunkRepository, HexStringChunkRepositoryWrapper}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait ChunkRepository[ChunkKey] {
  def chunks: Source[ChunkKey, NotUsed]
  def read(key: ChunkKey): Source[ByteString, Future[IOResult]]
  def write(key: ChunkKey): Sink[ByteString, Future[IOResult]]
}

object ChunkRepository {
  type BaseChunkRepository = ChunkRepository[String]

  def inMemory[T]: ChunkRepository[T] = {
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