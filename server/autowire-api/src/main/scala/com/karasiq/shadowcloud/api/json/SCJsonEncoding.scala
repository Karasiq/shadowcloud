package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import com.karasiq.shadowcloud.api.SCApiEncoding
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.model.{File, Path}
import com.karasiq.shadowcloud.serialization.json.SCJsonEncoders
import play.api.libs.json._

trait SCJsonEncoding extends SCApiEncoding {
  type ImplicitsT = SCJsonEncoders.type
  val implicits = SCJsonEncoders

  import implicits._

  private[this] val SlashBytes = ByteString(Path.Delimiter)

  type Encoder[T] = Writes[T]
  type Decoder[T] = Reads[T]

  def encode[T: Encoder](value: T): ByteString =
    toJsonBytes(value)

  def decode[T: Decoder](valueBytes: ByteString): T =
    fromJsonBytes[T](valueBytes)

  def encodePath(path: Path): ByteString =
    if (Path.isConventional(path)) ByteString(path.toString) else encode(path)

  def decodePath(json: ByteString): Path =
    if (json.startsWith(SlashBytes)) Path.fromString(json.utf8String) else decode[Path](json)

  def encodeFile(file: File): ByteString      = encode(file)
  def decodeFile(fileBytes: ByteString): File = decode[File](fileBytes)

  def encodeFiles(files: Seq[File]): ByteString      = encode(files)
  def decodeFiles(filesBytes: ByteString): Seq[File] = decode[Seq[File]](filesBytes)

  def encodeScope(scope: IndexScope): ByteString = encode(scope)

  def decodeScope(scopeBytes: ByteString): IndexScope = decode[IndexScope](scopeBytes)

  private[this] def toJsonBytes[T: Writes](value: T): ByteString =
    ByteString.fromArrayUnsafe(Json.toBytes(Json.toJson(value)))

  private[this] def fromJsonBytes[T: Reads](value: ByteString): T =
    Json
      .fromJson[T](Json.parse(value.toArray))
      .getOrElse(throw new IllegalArgumentException(s"Invalid json: ${value.utf8String}"))
}

object SCJsonEncoding extends SCJsonEncoding
