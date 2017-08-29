package com.karasiq.shadowcloud.api.json

import akka.util.ByteString
import com.trueaccord.scalapb.{GeneratedEnum, GeneratedEnumCompanion, GeneratedMessage, GeneratedMessageCompanion}
import play.api.libs.json._

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.crypto._
import com.karasiq.shadowcloud.utils.{Base64, HexString}

//noinspection ConvertExpressionToSAM
trait SCJsonEncoders {
  implicit val byteStringReads: Reads[ByteString] = Reads(value ⇒ JsSuccess(HexString.decode(value.as[JsString].value)))
  implicit val byteStringWrites: Writes[ByteString] = Writes(value ⇒ JsString(HexString.encode(value)))
  implicit val pathReads: Reads[Path] = Json.reads[Path]
  implicit val pathWrites: Writes[Path] = Json.writes[Path]
  implicit val serializedPropsReads: Reads[SerializedProps] = Json.reads[SerializedProps]
  implicit val serializedPropsWrites: Writes[SerializedProps] = Json.writes[SerializedProps]
  implicit val encryptionMethodReads: Reads[EncryptionMethod] = Json.reads[EncryptionMethod]
  implicit val encryptionMethodWrites: Writes[EncryptionMethod] = Json.writes[EncryptionMethod]
  implicit val hashingMethodReads: Reads[HashingMethod] = Json.reads[HashingMethod]
  implicit val hashingMethodWrites: Writes[HashingMethod] = Json.writes[HashingMethod]
  implicit val symmetricEncryptionParametersReads: Reads[SymmetricEncryptionParameters] = Json.reads[SymmetricEncryptionParameters]
  implicit val symmetricEncryptionParametersWrites: Writes[SymmetricEncryptionParameters] = Json.writes[SymmetricEncryptionParameters]
  implicit val asymmetricEncryptionParametersReads: Reads[AsymmetricEncryptionParameters] = Json.reads[AsymmetricEncryptionParameters]
  implicit val asymmetricEncryptionParametersWrites: Writes[AsymmetricEncryptionParameters] = Json.writes[AsymmetricEncryptionParameters]
  implicit val encryptionParametersReads: Reads[EncryptionParameters] = Json.reads[EncryptionParameters]
  implicit val encryptionParametersWrites: Writes[EncryptionParameters] = Json.writes[EncryptionParameters]
  implicit val timestampReads: Reads[Timestamp] = Json.reads[Timestamp]
  implicit val timestampWrites: Writes[Timestamp] = Json.writes[Timestamp]
  implicit val dataReads: Reads[Data] = Json.reads[Data]
  implicit val dataWrites: Writes[Data] = Json.writes[Data]
  implicit val checksumReads: Reads[Checksum] = Json.reads[Checksum]
  implicit val checksumWrites: Writes[Checksum] = Json.writes[Checksum]
  implicit val chunkReads: Reads[Chunk] = Json.reads[Chunk]
  implicit val chunkWrites: Writes[Chunk] = Json.writes[Chunk]
  implicit val fileReads: Reads[File] = Json.reads[File]
  implicit val fileWrites: Writes[File] = Json.writes[File]
  implicit val folderReads: Reads[Folder] = Json.reads[Folder]
  implicit val folderWrites: Writes[Folder] = Json.writes[Folder]

  implicit def generatedMessageReadWrites[T <: GeneratedMessage with com.trueaccord.scalapb.Message[T] : GeneratedMessageCompanion]: Reads[T] with Writes[T] = new Reads[T] with Writes[T] {
    def reads(json: JsValue): JsResult[T] = {
      val bytes = Base64.decode(json.as[String])
      JsSuccess(implicitly[GeneratedMessageCompanion[T]].parseFrom(bytes.toArray))
    }

    def writes(o: T): JsValue = {
      JsString(Base64.encode(ByteString(o.toByteArray)))
    }
  }

  implicit def generatedEnumReadWrites[T <: GeneratedEnum : GeneratedEnumCompanion]: Reads[T] with Writes[T] = new Reads[T] with Writes[T] {
    def reads(json: JsValue): JsResult[T] = {
      val stringValue = json.as[String]
      val value = implicitly[GeneratedEnumCompanion[T]].fromName(stringValue)
      value match {
        case Some(value) ⇒
          JsSuccess(value)

        case None ⇒
          JsError(s"Invalid enum value: $stringValue")
      }
    }

    def writes(o: T): JsValue = {
      JsString(o.name)
    }
  }
}