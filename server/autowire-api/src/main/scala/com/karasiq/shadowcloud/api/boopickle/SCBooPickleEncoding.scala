package com.karasiq.shadowcloud.api.boopickle

import akka.util.ByteString

import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.model.{File, Path}
import com.karasiq.shadowcloud.model.utils.IndexScope

trait SCBooPickleEncoding extends SCApiEncoding {
  type ImplicitsT = SCBooPickleEncoders.type
  val implicits = SCBooPickleEncoders

  import implicits._

  type Encoder[T] = Pickler[T]
  type Decoder[T] = Pickler[T]

  def encode[T: Pickler](value: T) = ByteString(Pickle.intoBytes(value))
  def decode[T: Pickler](valueBytes: ByteString) = Unpickle[T].fromBytes(valueBytes.toByteBuffer)
  def encodePath(path: Path) = encode(path)
  def decodePath(pathBytes: ByteString) = decode[Path](pathBytes)
  def encodeFile(file: File) = encode(file)
  def decodeFile(fileBytes: ByteString) = decode[File](fileBytes)
  def encodeScope(scope: IndexScope) = encode(scope)
  def decodeScope(scopeBytes: ByteString) = decode[IndexScope](scopeBytes)
}

object SCBooPickleEncoding extends SCBooPickleEncoding