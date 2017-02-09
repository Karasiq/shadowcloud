package com.karasiq.shadowcloud.storage.files

import java.nio.file.{StandardOpenOption, Path => FsPath}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.IncrementalIndexRepository
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * Uses local filesystem to store indexes
  * @param folder Root directory
  */
class FileIndexRepository(folder: FsPath)(implicit as: ActorSystem, am: ActorMaterializer) extends IncrementalIndexRepository {
  def keys: Source[Long, NotUsed] = {
    Source(FileSystemUtils.listFiles(folder))
      .filter(_.matches("\\d+"))
      .map(_.toLong)
  }

  def read(key: Long): Source[ByteString, Future[IOResult]] = {
    FileIO.fromPath(folder.resolve(key.toString))
  }

  def write(key: Long): Sink[ByteString, Future[IOResult]] = {
    FileIO.toPath(folder.resolve(key.toString), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }
}
