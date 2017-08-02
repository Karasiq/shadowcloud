package com.karasiq.shadowcloud.api

import java.util.Base64

import akka.util.ByteString

import com.karasiq.shadowcloud.index.{File, Path}

object SCApiEncoding {
  def toUrlSafe(data: ByteString): String = {
    Base64.getUrlEncoder.encodeToString(data.toArray)
  }

  def toBinary(string: String): ByteString = {
    ByteString(Base64.getUrlDecoder.decode(string))
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