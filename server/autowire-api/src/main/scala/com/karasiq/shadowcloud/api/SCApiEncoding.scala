package com.karasiq.shadowcloud.api

import akka.util.ByteString

import com.karasiq.shadowcloud.model.{File, Path}
import com.karasiq.shadowcloud.utils.encoding.Base64

object SCApiEncoding {
  def toUrlSafe(data: ByteString): String = {
    Base64.encode(data)
  }

  def toBinary(string: String): ByteString = {
    Base64.decode(string)
  }
}

trait SCApiEncoding {
  def encodePath(path: Path): ByteString
  def decodePath(pathString: ByteString): Path

  def encodeFile(file: File): ByteString
  def decodeFile(fileBytes: ByteString): File

  type ImplicitsT
  val implicits: ImplicitsT
}