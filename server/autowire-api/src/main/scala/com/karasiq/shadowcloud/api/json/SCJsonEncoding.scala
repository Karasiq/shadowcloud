package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import play.api.libs.json._

import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.model.{File, Path}

trait SCJsonEncoding extends SCApiEncoding {
  type ImplicitsT = SCJsonEncoders
  object implicits extends SCJsonEncoders

  import implicits._

  private[this] val SlashBytes = ByteString(Path.Delimiter)

  type Encoder[T] = Writes[T]
  type Decoder[T] = Reads[T]

  def encode[T: Encoder](value: T): ByteString = {
    toJsonBytes(value)
  }

  def decode[T: Decoder](valueBytes: ByteString): T = {
    fromJsonBytes[T](valueBytes)
  }

  def encodePath(path: Path): ByteString = {
    if (Path.isConventional(path)) ByteString(path.toString) else toJsonBytes(path)
  }

  def decodePath(json: ByteString): Path = {
    if (json.startsWith(SlashBytes)) Path.fromString(json.utf8String) else fromJsonBytes[Path](json)
  }

  def encodeFile(file: File): ByteString = {
    toJsonBytes(file)
  }

  def decodeFile(fileBytes: ByteString): File = {
    fromJsonBytes[File](fileBytes)
  }

  private[this] def toJsonBytes[T: Writes](value: T): ByteString = {
    ByteString(Json.toBytes(Json.toJson(value)))
  }

  private[this] def fromJsonBytes[T: Reads](value: ByteString): T = {
    Json.fromJson[T](Json.parse(value.toArray)).get
  }
}
