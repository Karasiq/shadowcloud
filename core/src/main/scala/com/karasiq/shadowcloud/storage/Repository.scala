package com.karasiq.shadowcloud.storage

import java.nio.file.{Files, Path}

import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileRepository
import com.karasiq.shadowcloud.storage.inmem.TrieMapRepository
import com.karasiq.shadowcloud.storage.wrappers.CategorizedRepository

import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait Repository[Key] {
  type Data = ByteString
  type Result = Future[IOResult]

  def keys: Source[Key, Result]
  def read(key: Key): Source[Data, Result]
  def write(key: Key): Sink[Data, Result]
}

object Repository {
  type BaseRepository = Repository[String]

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
