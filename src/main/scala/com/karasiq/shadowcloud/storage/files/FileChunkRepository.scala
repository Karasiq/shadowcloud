package com.karasiq.shadowcloud.storage.files

import java.nio.file.{StandardOpenOption, Path => FsPath}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import com.karasiq.shadowcloud.storage.ChunkRepository
import com.karasiq.shadowcloud.utils.{FileSystemUtils, Utils}

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * Uses local filesystem to store data chunks
  * @param folder Root directory
  */
class FileChunkRepository(folder: FsPath)(implicit as: ActorSystem, am: ActorMaterializer) extends ChunkRepository {
  def chunks: Source[ChunkKey, NotUsed] = {
    Source(FileSystemUtils.listFiles(folder))
      .filter(_.matches("[a-fA-F0-9]+"))
      .map(Utils.parseHexString)
  }

  def read(chunk: ChunkKey): Source[ChunkKey, Future[IOResult]] = {
    FileIO.fromPath(resolvePath(chunk))
  }

  def write(chunk: ChunkKey): Sink[ChunkKey, Future[IOResult]] = {
    FileIO.toPath(resolvePath(chunk), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }

  protected def resolvePath(hash: ChunkKey): FsPath = {
    folder.resolve(Utils.toHexString(hash))
  }
}
