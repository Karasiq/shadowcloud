package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import play.api.libs.json._

import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.model.{File, Path}

trait SCJsonEncoding extends SCApiEncoding {
  type ImplicitsT = SCJsonEncoders
  object implicits extends SCJsonEncoders

  import implicits._

  private[this] def toJsonBytes[T: Writes](value: T): ByteString = {
    ByteString(Json.toBytes(Json.toJson(value)))
  }

  private[this] def fromJsonBytes[T: Reads](value: ByteString): T = {
    Json.fromJson[T](Json.parse(value.toArray)).get
  }

  def encodePath(path: Path): ByteString = {
    toJsonBytes(path)
  }

  def decodePath(json: ByteString): Path = {
    fromJsonBytes[Path](json)
  }

  def encodeFile(file: File): ByteString = {
    toJsonBytes(file)
  }

  def decodeFile(fileBytes: ByteString): File = {
    fromJsonBytes[File](fileBytes)
  }
}
