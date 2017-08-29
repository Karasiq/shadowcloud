package com.karasiq.shadowcloud.test.utils

import java.io.FileNotFoundException
import java.nio.file.Files

import scala.concurrent.Future
import scala.util.Try

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

object ResourceUtils {
  def getPathOption(name: String): Option[java.nio.file.Path] = {
    Try(new java.io.File(getClass.getClassLoader.getResource(name).toURI).toPath).toOption
  }

  def getPath(name: String): java.nio.file.Path = {
    getPathOption(name).getOrElse(throw new FileNotFoundException(name))
  }

  def toBytes(name: String): ByteString = {
    getPathOption(name).fold(ByteString.empty)(path ⇒ ByteString(Files.readAllBytes(path)))
  }

  def toString(name: String): String = {
    toBytes(name).utf8String
  }

  def toStream(name: String): Source[ByteString, Future[IOResult]] = {
    getPathOption(name) match {
      case Some(path) ⇒
        FileIO.fromPath(path)

      case None ⇒
        Source.empty[ByteString].mapMaterializedValue(_ ⇒ Future.successful(IOResult.createSuccessful(0L)))
    }
  }
}
