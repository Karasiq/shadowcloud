package com.karasiq.shadowcloud.storage

import java.nio.file.{Files, Path}

import scala.collection.concurrent.{TrieMap, Map ⇒ CMap}
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

import akka.stream.Materializer
import akka.util.ByteString

import com.karasiq.shadowcloud.storage.files.FileRepository
import com.karasiq.shadowcloud.storage.inmem.ConcurrentMapRepository

/**
  * Standard repositories
  */
private[shadowcloud] object Repositories {
  def fromConcurrentMap[T](map: CMap[T, ByteString]): Repository[T] = {
    new ConcurrentMapRepository(map)
  }

  def inMemory[T]: Repository[T] = {
    fromConcurrentMap(TrieMap.empty)
  }

  def fromDirectory(directory: Path)(implicit ec: ExecutionContext, mat: Materializer): PathTreeRepository = {
    require(Files.isDirectory(directory), s"Not a directory: $directory")
    FileRepository(directory)
  }
}
