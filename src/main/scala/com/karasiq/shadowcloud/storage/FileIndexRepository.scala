package com.karasiq.shadowcloud.storage

import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{FileVisitResult, Files, SimpleFileVisitor, StandardOpenOption, Path => FsPath}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{FileIO, Source}

import scala.collection.mutable.ArrayBuffer
import scala.language.postfixOps

class FileIndexRepository(folder: FsPath)(implicit as: ActorSystem, am: ActorMaterializer) extends TimeIndexRepository {
  private def listFiles() = {
    val files = ArrayBuffer[FsPath]()
    Files.walkFileTree(folder, new SimpleFileVisitor[FsPath] {
      override def visitFile(file: FsPath, attrs: BasicFileAttributes) = {
        if (file.getFileName.toString.matches("\\d+")) files += file
        FileVisitResult.CONTINUE
      }

      override def preVisitDirectory(dir: FsPath, attrs: BasicFileAttributes) = {
        if (dir == folder) FileVisitResult.CONTINUE else FileVisitResult.SKIP_SUBTREE
      }
    })
    files
  }

  def keys = {
    Source(listFiles().toVector)
      .map(_.getFileName.toString.toLong)
  }


  def read(key: Long) = {
    FileIO.fromPath(folder.resolve(key.toString))
  }

  def write(key: Long) = {
    FileIO.toPath(folder.resolve(key.toString), Set(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
  }
}
