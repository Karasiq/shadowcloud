package com.karasiq.shadowcloud.test.utils

import java.nio.file.Files

import scala.concurrent.Future

import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

object ResourceUtils {
  def getPath(name: String): java.nio.file.Path = {
    new java.io.File(getClass.getClassLoader.getResource(name).toURI).toPath
  }

  def toBytes(name: String): ByteString = {
    ByteString(Files.readAllBytes(getPath(name)))
  }

  def toString(name: String): String = {
    toBytes(name).utf8String
  }

  def toStream(name: String): Source[ByteString, Future[IOResult]] = {
    FileIO.fromPath(getPath(name))
  }
}
