package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, StandardOpenOption, Path => FSPath}

import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Sink, Source}
import com.karasiq.shadowcloud.storage.wrappers.CategorizedRepository
import com.karasiq.shadowcloud.utils.FileSystemUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
  * Uses local filesystem to store data
  * @param folder Root directory
  */
private[storage] final class FileRepository(folder: FSPath)(implicit ec: ExecutionContext) extends CategorizedRepository[String, String] {
  def keys: Source[(String, String), Result] = {
    val filesFuture = Future {
      val subFolders = FileSystemUtils.listSubItems(folder, includeFiles = false)
      val subTrees = subFolders.map(dir ⇒ (dir, FileSystemUtils.listSubItems(dir, includeDirs = false)))
      subTrees.flatMap { case (parent, subDirs) ⇒
        subDirs.map(dir ⇒ (parent.getFileName.toString, dir.getFileName.toString))
      }
    }
    Source.fromFuture(filesFuture)
      .mapConcat(identity)
      .mapMaterializedValue { _ ⇒ 
        filesFuture
          .map(files ⇒ IOResult(files.length, Success(Done)))
          .recover(PartialFunction(error ⇒ IOResult(0, Failure(error))))
      }
  }

  def read(key: (String, String)): Source[Data, Result] = {
    FileIO.fromPath(toPath(key))
  }

  def write(key: (String, String)): Sink[Data, Result] = {
    val destination = toPath(key)
    Files.createDirectories(destination.getParent)
    FileIO.toPath(destination, Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }
  
  private[this] def toPath(key: (String, String)): FSPath = {
    folder.resolve(key._1).resolve(key._2)
  }
}
