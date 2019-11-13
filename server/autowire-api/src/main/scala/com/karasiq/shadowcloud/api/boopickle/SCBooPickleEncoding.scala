package com.karasiq.shadowcloud.api.boopickle

import akka.util.ByteString
import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.model.{File, Path}
import com.karasiq.shadowcloud.serialization.boopickle.SCBooPickleEncoders

trait SCBooPickleEncoding extends SCApiEncoding {
  type ImplicitsT = SCBooPickleEncoders.type
  val implicits: SCBooPickleEncoders.type = SCBooPickleEncoders

  import implicits._

  type Encoder[T] = Pickler[T]
  type Decoder[T] = Pickler[T]

  def encode[T: Pickler](value: T)                  = ByteString(Pickle.intoBytes(value))
  def decode[T: Pickler](valueBytes: ByteString): T = Unpickle[T].fromBytes(valueBytes.toByteBuffer)

  def encodePath(path: Path): ByteString      = encode(path)
  def decodePath(pathBytes: ByteString): Path = decode[Path](pathBytes)

  def encodeFile(file: File): ByteString      = encode(file)
  def decodeFile(fileBytes: ByteString): File = decode[File](fileBytes)

  def encodeFiles(file: Seq[File]): ByteString      = encode(file)
  def decodeFiles(fileBytes: ByteString): Seq[File] = decode[Seq[File]](fileBytes)

  def encodeScope(scope: IndexScope): ByteString      = encode(scope)
  def decodeScope(scopeBytes: ByteString): IndexScope = decode[IndexScope](scopeBytes)
}

object SCBooPickleEncoding extends SCBooPickleEncoding
