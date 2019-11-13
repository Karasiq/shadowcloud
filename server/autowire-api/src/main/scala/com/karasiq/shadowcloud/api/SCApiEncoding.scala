package com.karasiq.shadowcloud.api

import akka.util.ByteString
import com.karasiq.common.encoding.Base64
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.model.{File, Path}

import scala.language.higherKinds

object SCApiEncoding {
  def toUrlSafe(data: ByteString): String =
    Base64.encode(data)

  def toBinary(string: String): ByteString =
    Base64.decode(string)
}

trait SCApiEncoding {
  type Encoder[T]
  type Decoder[T]

  // Generic encoding
  def encode[T: Encoder](value: T): ByteString
  def decode[T: Decoder](valueBytes: ByteString): T

  // Static encoding
  def encodePath(path: Path): ByteString
  def decodePath(pathBytes: ByteString): Path

  def encodeFile(file: File): ByteString
  def decodeFile(fileBytes: ByteString): File

  def encodeFiles(files: Seq[File]): ByteString
  def decodeFiles(filesBytes: ByteString): Seq[File]

  def encodeScope(scope: IndexScope): ByteString
  def decodeScope(scopeBytes: ByteString): IndexScope

  type ImplicitsT
  val implicits: ImplicitsT
}
