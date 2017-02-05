package com.karasiq.shadowcloud.storage

import java.nio.file.{StandardOpenOption, Path => FsPath}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.language.postfixOps

/**
  * Uses local filesystem to store indexes
  * @param folder Root directory
  */
class FileIndexRepository(folder: FsPath)(implicit as: ActorSystem, am: ActorMaterializer) extends TimeIndexRepository {
  def keys = {
    Source(FileSystemUtils.listFiles(folder))
      .filter(_.matches("\\d+"))
      .map(_.toLong)
  }

  def read(key: Long) = {
    FileIO.fromPath(folder.resolve(key.toString))
  }

  def write(key: Long) = {
    FileIO.toPath(folder.resolve(key.toString), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }
}
