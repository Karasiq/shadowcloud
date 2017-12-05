package com.karasiq.shadowcloud.api.json

import akka.Done
import akka.util.ByteString
import com.trueaccord.scalapb.{GeneratedEnum, GeneratedEnumCompanion, GeneratedMessage, GeneratedMessageCompanion}
import play.api.libs.json._

import com.karasiq.common.encoding.{Base64, HexString}
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex}
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.crypto._
import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyProps, KeySet}
import com.karasiq.shadowcloud.model.utils._
import com.karasiq.shadowcloud.model.utils.GCReport.{RegionGCState, StorageGCState}
import com.karasiq.shadowcloud.model.utils.RegionStateReport.{RegionStatus, StorageStatus}

//noinspection ConvertExpressionToSAM
trait SCJsonEncoders {
  implicit val byteStringFormat = Format[ByteString](
    Reads(value ⇒ JsSuccess(HexString.decode(value.as[String]))),
    Writes(value ⇒ JsString(HexString.encode(value)))
  )

  implicit val akkaDoneFormat = Format[Done](
    Reads(value ⇒ if (value.asOpt[JsString].exists(_.value == "Done")) JsSuccess(Done) else JsError()),
    Writes(_ ⇒ JsString("Done"))
  )

  implicit val pathFormat = Json.format[Path]
  implicit val serializedPropsFormat = Json.format[SerializedProps]
  implicit val encryptionMethodFormat = Json.format[EncryptionMethod]
  implicit val hashingMethodFormat = Json.format[HashingMethod]
  implicit val symmetricEncryptionParametersFormat = Json.format[SymmetricEncryptionParameters]
  implicit val asymmetricEncryptionParametersFormat = Json.format[AsymmetricEncryptionParameters]
  implicit val encryptionParametersFormat = Json.format[EncryptionParameters]
  implicit val signMethodFormat = Json.format[SignMethod]
  implicit val signParametersFormat = Json.format[SignParameters]
  implicit val timestampFormat = Json.format[Timestamp]
  implicit val dataFormat = Json.format[Data]
  implicit val checksumFormat = Json.format[Checksum]
  implicit val chunkFormat = Json.format[Chunk]
  implicit val fileFormat = Json.format[File]
  implicit val folderFormat = Json.format[Folder]

  implicit def pathMapFormat[V: Format]: Format[Map[Path, V]] = {
    def pathToString(path: Path) = {
      if (Path.isConventional(path)) path.toString else Json.toJson(path).toString()
    }

    def stringToPath(pathString: String) = {
      if (pathString.startsWith(Path.Delimiter)) Path.fromString(pathString) else Json.parse(pathString).as[Path]
    }

    jsonMapFormat(pathToString, stringToPath)
  }

  implicit def sequenceNrMapFormat[V: Format]: Format[Map[SequenceNr, V]] = {
    jsonMapFormat(_.toString, _.toLong)
  }

  implicit val chunkIndexFormat = Json.format[ChunkIndex]
  implicit val folderIndexFormat = Json.format[FolderIndex]
  implicit val folderDiffFormat = Json.format[FolderDiff]
  implicit val folderIndexDiffFormat = Json.format[FolderIndexDiff]
  implicit val chunkIndexDiffFormat = Json.format[ChunkIndexDiff]
  implicit val indexDiffFormat = Json.format[IndexDiff]
  implicit val fileAvailabilityFormat = Json.format[FileAvailability]
  implicit val storageGCStateFormat = Json.format[StorageGCState]
  implicit val regionGCStateFormat = Json.format[RegionGCState]
  implicit val gcReportFormat = Json.format[GCReport]
  implicit val syncReportFormat = Json.format[SyncReport]
  implicit val storageStatusFormat = Json.format[StorageStatus]
  implicit val regionStatusFormat = Json.format[RegionStatus]
  implicit val regionStateReportFormat = Json.format[RegionStateReport]
  implicit val storageHealthFormat = Json.format[StorageHealth]
  implicit val regionHealthFormat = Json.format[RegionHealth]

  implicit val keySetFormat = Json.format[KeySet]
  implicit val keyPropsFormat = Json.format[KeyProps]
  implicit val keyChainFormat = Json.format[KeyChain]

  implicit object IndexScopeFormat extends Format[IndexScope] {
    def writes(o: IndexScope): JsValue = o match {
      case IndexScope.Current ⇒
        JsString("Current")

      case IndexScope.Persisted ⇒
        JsString("Persisted")

      case IndexScope.UntilSequenceNr(sequenceNr) ⇒
        JsObject(Map("sequenceNr" → JsNumber(sequenceNr)))

      case IndexScope.UntilTime(timestamp) ⇒
        JsObject(Map("timestamp" → JsNumber(timestamp)))
    }

    def reads(json: JsValue): JsResult[IndexScope] = json match {
      case JsString("Current") ⇒
        JsSuccess(IndexScope.Current)

      case JsString("Persisted") ⇒
        JsSuccess(IndexScope.Persisted)

      case JsObject(values) if values.contains("sequenceNr") ⇒
        values("sequenceNr") match {
          case JsNumber(sequenceNr) if sequenceNr.isValidLong ⇒
            JsSuccess(IndexScope.UntilSequenceNr(sequenceNr.longValue()))

          case value ⇒
            JsError(s"Invalid sequence number: $value")
        }

      case JsObject(values) if values.contains("timestamp") ⇒
        values("timestamp") match {
          case JsNumber(timestamp) if timestamp.isValidLong ⇒
            JsSuccess(IndexScope.UntilTime(timestamp.longValue()))

          case value ⇒
            JsError(s"Invalid timestamp: $value")
        }

      case value ⇒
        JsError(s"Invalid index scope: $value")
    }
  }

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

  private[this] def jsonMapFormat[K, V: Format](toString: K ⇒ String, toKey: String ⇒ K): Format[Map[K, V]] = {
    Format[Map[K, V]](
      Reads.mapReads[K, V](pathString ⇒ JsSuccess(toKey(pathString))),
      Writes(map ⇒ Json.toJson(map.map(kv ⇒ (toString(kv._1), kv._2))))
    )
  }
}

object SCJsonEncoders extends SCJsonEncoders