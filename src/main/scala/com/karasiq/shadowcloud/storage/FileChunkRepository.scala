package com.karasiq.shadowcloud.storage

import java.nio.file.{StandardOpenOption, Path => FsPath}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}
import com.karasiq.shadowcloud.utils.{FileSystemUtils, Utils}

import scala.language.postfixOps

/**
  * Uses local filesystem to store data chunks
  * @param folder Root directory
  */
class FileChunkRepository(folder: FsPath)(implicit as: ActorSystem, am: ActorMaterializer) extends ChunkRepository {
  def chunks = {
    Source(FileSystemUtils.listFiles(folder))
      .filter(_.matches("[a-fA-F0-9]+"))
      .map(Utils.parseHexString)
  }

  def read(chunk: ChunkKey) = {
    FileIO.fromPath(resolvePath(chunk))
  }

  def write(chunk: ChunkKey) = {
    FileIO.toPath(resolvePath(chunk), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }

  protected def resolvePath(hash: ChunkKey) = {
    folder.resolve(Utils.toHexString(hash))
  }
}
