package com.karasiq.shadowcloud.serialization.boopickle

import java.time.Instant

import akka.Done
import akka.util.ByteString
import boopickle._
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, FolderDiff, FolderIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.index.{ChunkIndex, FolderIndex, IndexData}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.crypto._
import com.karasiq.shadowcloud.model.keys.{KeyChain, KeyProps, KeySet}
import com.karasiq.shadowcloud.model.utils.GCReport.{RegionGCState, StorageGCState}
import com.karasiq.shadowcloud.model.utils.RegionStateReport.{RegionStatus, StorageStatus}
import com.karasiq.shadowcloud.model.utils._
import com.karasiq.shadowcloud.ui.Challenge
import scalapb.{GeneratedEnum, GeneratedEnumCompanion, GeneratedMessage, GeneratedMessageCompanion}

//noinspection TypeAnnotation
trait SCBooPickleEncoders extends Base with BasicImplicitPicklers with TransformPicklers with TuplePicklers {
  protected object MacroPicklers extends MaterializePicklerFallback
  import MacroPicklers.generatePickler

  implicit object byteStringFormat extends Pickler[ByteString] {
    def pickle(obj: ByteString)(implicit state: PickleState): Unit = {
      state.immutableRefFor(obj) match {
        case Some(idx) ⇒
          state.enc.writeRawInt(-idx)

        case None ⇒
          state.enc.writeByteArray(obj.toArray)
          state.addImmutableRef(obj)
      }
    }

    def unpickle(implicit state: UnpickleState): ByteString = {
      state.dec.readRawInt match {
        case idx if idx < 0 ⇒
          state.immutableFor[ByteString](-idx)

        case length ⇒
          val bytes = ByteString(state.dec.readByteArray(length))
          state.addImmutableRef(bytes)
          bytes
      }
    }
  }

  implicit object instantFormat extends Pickler[Instant] {
    override def pickle(obj: Instant)(implicit state: PickleState): Unit = {
      state.enc.writeLong(obj.toEpochMilli)
    }

    override def unpickle(implicit state: UnpickleState): Instant = {
      Instant.ofEpochMilli(state.dec.readLong)
    }
  }

  implicit val akkaDoneFormat: Pickler[Done]        = ConstPickler(Done)
  implicit val akkaDoneFormat1                      = ConstPickler(Done)
  implicit val pathFormat                           = generatePickler[Path]
  implicit val serializedPropsFormat                = generatePickler[SerializedProps]
  implicit val encryptionMethodFormat               = generatePickler[EncryptionMethod]
  implicit val hashingMethodFormat                  = generatePickler[HashingMethod]
  implicit val symmetricEncryptionParametersFormat  = generatePickler[SymmetricEncryptionParameters]
  implicit val asymmetricEncryptionParametersFormat = generatePickler[AsymmetricEncryptionParameters]
  implicit val encryptionParametersFormat           = generatePickler[EncryptionParameters]

  implicit val signMethodFormat     = generatePickler[SignMethod]
  implicit val signParametersFormat = generatePickler[SignParameters]
  implicit val timestampFormat      = generatePickler[Timestamp]
  implicit val dataFormat           = generatePickler[Data]
  implicit val checksumFormat       = generatePickler[Checksum]
  implicit val chunkFormat          = generatePickler[Chunk]
  implicit val fileFormat           = generatePickler[File]
  implicit val folderFormat         = generatePickler[Folder]

  implicit val chunkIndexFormat            = generatePickler[ChunkIndex]
  implicit val folderIndexFormat           = generatePickler[FolderIndex]
  implicit val folderDiffFormat            = generatePickler[FolderDiff]
  implicit val folderIndexDiffFormat       = generatePickler[FolderIndexDiff]
  implicit val chunkIndexDiffFormat        = generatePickler[ChunkIndexDiff]
  implicit val indexDiffFormat             = generatePickler[IndexDiff]
  implicit val indexDataFormat             = generatePickler[IndexData]
  implicit val fileAvailabilityFormat      = generatePickler[FileAvailability]
  implicit val storageGCStateFormat        = generatePickler[StorageGCState]
  implicit val regionGCStateFormat         = generatePickler[RegionGCState]
  implicit val gCReportFormat              = generatePickler[GCReport]
  implicit val syncReportFormat            = generatePickler[SyncReport]
  implicit val storageStatusFormat         = generatePickler[StorageStatus]
  implicit val regionStatusFormat          = generatePickler[RegionStatus]
  implicit val regionStateReportFormat     = generatePickler[RegionStateReport]
  implicit val storageHealthFormat         = generatePickler[StorageHealth]
  implicit val regionHealthFormat          = generatePickler[RegionHealth]
  implicit val indexScopeFormat            = generatePickler[IndexScope]
  implicit val keySetFormat                = generatePickler[KeySet]
  implicit val keyPropsFormat              = generatePickler[KeyProps]
  implicit val keyChainFormat              = generatePickler[KeyChain]
  implicit val challengeAnswerFormatFormat = generatePickler[Challenge.AnswerFormat]
  implicit val challengeFormat             = generatePickler[Challenge]

  implicit def generatedMessagePickler[T <: GeneratedMessage with scalapb.Message[T]: GeneratedMessageCompanion]: Pickler[T] = new Pickler[T] {
    def pickle(obj: T)(implicit state: PickleState): Unit = {
      state.enc.writeByteArray(obj.toByteArray)
    }

    def unpickle(implicit state: UnpickleState): T = {
      implicitly[GeneratedMessageCompanion[T]].parseFrom(state.dec.readByteArray())
    }
  }

  implicit def generatedEnumPickler[T <: GeneratedEnum: GeneratedEnumCompanion]: Pickler[T] = new Pickler[T] {
    def pickle(obj: T)(implicit state: PickleState): Unit = {
      state.enc.writeInt(obj.value)
    }

    def unpickle(implicit state: UnpickleState): T = {
      implicitly[GeneratedEnumCompanion[T]].fromValue(state.dec.readInt)
    }
  }
}

object SCBooPickleEncoders extends SCBooPickleEncoders
