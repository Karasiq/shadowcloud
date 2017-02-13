package com.karasiq.shadowcloud.storage.files

import java.nio.file.{StandardOpenOption, Path => FsPath}

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.BaseChunkRepository
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * Uses local filesystem to store data chunks
  * @param folder Root directory
  */
class FileChunkRepository(folder: FsPath) extends BaseChunkRepository {
  def chunks: Source[String, NotUsed] = {
    Source(FileSystemUtils.listFiles(folder))
  }

  def read(key: String): Source[ByteString, Future[IOResult]] = {
    FileIO.fromPath(folder.resolve(key))
  }

  def write(key: String): Sink[ByteString, Future[IOResult]] = {
    FileIO.toPath(folder.resolve(key), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }
}
