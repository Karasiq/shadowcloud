package com.karasiq.shadowcloud.storage.files

import java.nio.file.{StandardOpenOption, Path => FsPath}

import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import com.karasiq.shadowcloud.storage.Repository.BaseRepository
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Uses local filesystem to store data
  * @param folder Root directory
  */
private[storage] final class FileRepository(folder: FsPath)(implicit ec: ExecutionContext) extends BaseRepository {
  def keys: Source[String, Result] = {
    val future = Future(FileSystemUtils.listFiles(folder))
    Source
      .fromFuture(future)
      .mapConcat(identity)
      .mapMaterializedValue { _ ⇒
        future
          .map(files ⇒ IOResult(files.length, Success(Done)))
          .recover(PartialFunction(error ⇒ IOResult(0, Failure(error))))
      }
  }

  def read(key: String): Source[Data, Result] = {
    FileIO.fromPath(folder.resolve(key))
  }

  def write(key: String): Sink[Data, Result] = {
    FileIO.toPath(folder.resolve(key), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }
}
