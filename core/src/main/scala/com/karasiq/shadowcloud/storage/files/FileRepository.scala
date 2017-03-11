package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, StandardOpenOption, Path => FSPath}

import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import com.karasiq.shadowcloud.storage.{BaseRepository, CategorizedRepository, Repository}
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

private[storage] object FileRepository {
  def singleDir(folder: FSPath)(implicit ec: ExecutionContext): BaseRepository = {
    new FileRepository(folder)
  }

  def withSubDirs(root: FSPath)(implicit ec: ExecutionContext): CategorizedRepository[String, String] = {
    def newSubDirectory(dir: String): BaseRepository = {
      val path = root.resolve(dir)
      Files.createDirectories(path)
      singleDir(path)
    }
    Repository.fromSubRepositories { () ⇒
      val subDirs = FileSystemUtils.listSubItems(root, includeFiles = false)
        .map(dir ⇒ (dir.getFileName.toString, singleDir(dir)))
        .toMap
      subDirs.withDefault(newSubDirectory)
    }
  }
}

/**
  * Uses local filesystem to store data
  * @param folder Root directory
  */
private[storage] final class FileRepository(folder: FSPath)(implicit ec: ExecutionContext) extends BaseRepository {
  def keys: Source[String, Result] = {
    val filesFuture = Future {
      val subFolders = FileSystemUtils.listSubItems(folder, includeDirs = false)
      subFolders.map(_.getFileName.toString)
    }
    Source.fromFuture(filesFuture)
      .mapConcat(identity)
      .mapMaterializedValue { _ ⇒ 
        filesFuture
          .map(files ⇒ IOResult(files.length, Success(Done)))
          .recover(PartialFunction(error ⇒ IOResult(0, Failure(error))))
      }
  }

  def read(key: String): Source[Data, Result] = {
    FileIO.fromPath(toPath(key))
  }

  def write(key: String): Sink[Data, Result] = {
    val destination = toPath(key)
    Files.createDirectories(destination.getParent)
    FileIO.toPath(destination, Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }

  def delete(key: String): Result = {
    val future = Future {
      val file = toPath(key)
      val size = Files.size(file)
      Files.delete(file)
      IOResult(size, Success(Done))
    }
    future.recover { case error ⇒ IOResult(0, Failure(error)) }
  }

  private[this] def toPath(key: String): FSPath = {
    folder.resolve(key)
  }
}
