package com.karasiq.shadowcloud.storage

import java.nio.file.{Files, Path}

import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileIndexRepository
import com.karasiq.shadowcloud.storage.inmem.InMemoryIndexRepository
import com.karasiq.shadowcloud.storage.wrappers.{NumericIndexRepository, NumericIndexRepositoryWrapper}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

trait IndexRepository[Key] {
  def keys: Source[Key, _]
  def read(key: Key): Source[ByteString, _]
  def write(key: Key): Sink[ByteString, _]
}

trait BaseIndexRepository extends IndexRepository[String]

object IndexRepository {
  def inMemory: BaseIndexRepository = {
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