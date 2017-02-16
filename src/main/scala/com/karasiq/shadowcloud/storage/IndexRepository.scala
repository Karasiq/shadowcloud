package com.karasiq.shadowcloud.storage

import java.nio.file.{Files, Path}

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileIndexRepository
import com.karasiq.shadowcloud.storage.inmem.InMemoryIndexRepository
import com.karasiq.shadowcloud.storage.wrappers.{NumericIndexRepository, NumericIndexRepositoryWrapper}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait IndexRepository[Key] {
  def keys: Source[Key, NotUsed]
  def read(key: Key): Source[ByteString, Future[IOResult]]
  def write(key: Key): Sink[ByteString, Future[IOResult]]
}

object IndexRepository {
  type BaseIndexRepository = IndexRepository[String]

  def inMemory[T]: IndexRepository[T] = {
    new InMemoryIndexRepository
  }

  def fromDirectory(directory: Path)(implicit ec: ExecutionContext): BaseIndexRepository = {
    require(Files.isDirectory(directory), s"Not directory: $directory")
    new FileIndexRepository(directory)
  }

  def numeric(underlying: BaseIndexRepository): NumericIndexRepository = {
    new NumericIndexRepositoryWrapper(underlying)
  }
}