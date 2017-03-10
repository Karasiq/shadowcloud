package com.karasiq.shadowcloud.storage

import java.nio.file.{Files, Path}

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileRepository
import com.karasiq.shadowcloud.storage.inmem.TrieMapRepository

import scala.collection.concurrent.TrieMap
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

/**
  * Standard repositories
  */
private[shadowcloud] object Repositories {
  def fromTrieMap[T](map: TrieMap[T, ByteString]): Repository[T] = {
    new TrieMapRepository(map)
  }

  def inMemory[T]: Repository[T] = {
    fromTrieMap(TrieMap.empty)
  }

  def fromDirectory(directory: Path)(implicit ec: ExecutionContext): CategorizedRepository[String, String] = {
    require(Files.isDirectory(directory), s"Not directory: $directory")
    new FileRepository(directory)
  }
}
