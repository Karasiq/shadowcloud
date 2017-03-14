package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, StandardOpenOption, Path => FSPath}

import akka.stream.scaladsl.{FileIO, Sink, Source}
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.storage.{BaseRepository, CategorizedRepository, Repository, StorageIOResult}
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

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
    Repository.fromSubRepositories(root.toString, { () ⇒
      val subDirs = FileSystemUtils.listSubItems(root, includeFiles = false)
        .map(dir ⇒ (dir.getFileName.toString, singleDir(dir)))
        .toMap
      subDirs.withDefault(newSubDirectory)
    })
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
        val path = folder.toString
        StorageUtils.wrapFuture(path, filesFuture.map(files ⇒ StorageIOResult.Success(path, files.length)))
      }
  }

  def read(key: String): Source[Data, Result] = {
    val path = toFilePath(key)
    FileIO.fromPath(path)
      .mapMaterializedValue(StorageUtils.wrapIOResult(path.toString, _))
  }

  def write(key: String): Sink[Data, Result] = {
    val path = toFilePath(key)
    Files.createDirectories(path.getParent)
    FileIO.toPath(path, Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
      .mapMaterializedValue(StorageUtils.wrapIOResult(path.toString, _))
  }

  def delete(key: String): Result = {
    val file = toFilePath(key)
    ioOperation(file.toString, {
      val size = Files.size(file)
      Files.delete(file)
      size
    })
  }

  private[this] def toFilePath(key: String): FSPath = {
    folder.resolve(key)
  }

  private[this] def ioOperation(path: String, f: ⇒ Long): Future[StorageIOResult] = {
    StorageUtils.wrapFuture(path, Future(StorageIOResult.Success(path, f)))
  }
}
