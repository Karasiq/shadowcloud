package com.karasiq.shadowcloud.api

import scala.language.higherKinds

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
  type Encoder[T]
  type Decoder[T]

  def encode[T: Encoder](value: T): ByteString
  def decode[T: Decoder](valueBytes: ByteString): T
  
  def encodePath(path: Path): ByteString
  def decodePath(pathBytes: ByteString): Path

  def encodeFile(file: File): ByteString
  def decodeFile(fileBytes: ByteString): File

  type ImplicitsT
  val implicits: ImplicitsT
}