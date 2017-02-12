package com.karasiq.shadowcloud.storage.files

import java.nio.file.{StandardOpenOption, Path => FsPath}

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.ByteString
import com.karasiq.shadowcloud.storage.ChunkRepository
import com.karasiq.shadowcloud.utils.{FileSystemUtils, Utils}

import scala.concurrent.Future
import scala.language.postfixOps

/**
  * Uses local filesystem to store data chunks
  * @param folder Root directory
  */
class FileChunkRepository(folder: FsPath)(implicit as: ActorSystem, am: ActorMaterializer) extends ChunkRepository[ByteString] {
  def chunks: Source[ByteString, NotUsed] = {
    Source(FileSystemUtils.listFiles(folder))
      .filter(_.matches("[a-fA-F0-9]+"))
      .map(Utils.parseHexString)
  }

  def read(chunk: ByteString): Source[ByteString, Future[IOResult]] = {
    FileIO.fromPath(resolvePath(chunk))
  }

  def write(chunk: ByteString): Sink[ByteString, Future[IOResult]] = {
    FileIO.toPath(resolvePath(chunk), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }

  protected def resolvePath(hash: ByteString): FsPath = {
    folder.resolve(Utils.toHexString(hash))
  }
}
