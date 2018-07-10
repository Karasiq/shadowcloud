package com.karasiq.shadowcloud.serialization

import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.model.crypto.EncryptionParameters
import com.karasiq.shadowcloud.serialization.protobuf.index.{SerializedIndexData, SerializedKeyData}

private[shadowcloud] trait IndexSerialization {
  def wrapIndexFrame(data: IndexData): SerializedIndexData
  def unwrapIndexFrame(data: SerializedIndexData): IndexData
  def wrapKey(parameters: EncryptionParameters): SerializedKeyData
  def unwrapKey(data: SerializedKeyData): EncryptionParameters
}

private[shadowcloud] object IndexSerialization {
  def apply()(implicit sc: ShadowCloudExtension): IndexSerialization = {
    new DefaultIndexSerialization()
  }
}

private[shadowcloud] final class DefaultIndexSerialization(implicit sc: ShadowCloudExtension) extends IndexSerialization  {
  def wrapIndexFrame(data: IndexData): SerializedIndexData = {
    val format = sc.config.serialization.indexFormat
    val bytes = format match {
      case "default" | "" ⇒
        sc.serialization.toBytes(data)

      case "boopickle" ⇒

        ByteString(Pickle.intoBytes(data))

      case "json" ⇒

        ByteString.fromArrayUnsafe(Json.toBytes(Json.toJson(data)))
    }
    SerializedIndexData(format, bytes)
  }

  def unwrapIndexFrame(data: SerializedIndexData): IndexData = data.format match {
    case "default" | "" ⇒
      sc.serialization.fromBytes[IndexData](data.data)

    case "boopickle" ⇒

      Unpickle[IndexData].fromBytes(data.data.toByteBuffer)

    case "json" ⇒

      Json.parse(data.data.toArray).as[IndexData]
  }

  def wrapKey(parameters: EncryptionParameters): SerializedKeyData = {
    val format = sc.config.serialization.indexFormat
    val bytes = format match {
      case "default" | "" ⇒
        sc.serialization.toBytes(parameters)

      case "boopickle" ⇒

        ByteString(Pickle.intoBytes(parameters))

      case "json" ⇒

        ByteString.fromArrayUnsafe(Json.toBytes(Json.toJson(parameters)))
    }
    SerializedKeyData(format, bytes)
  }

  def unwrapKey(data: SerializedKeyData): EncryptionParameters = {
    data.format match {
      case "default" | "" ⇒
        sc.serialization.fromBytes[EncryptionParameters](data.data)

      case "json" ⇒

        Json.parse(data.data.toArray).as[EncryptionParameters]

      case "boopickle" ⇒

        Unpickle[EncryptionParameters].fromBytes(data.data.toByteBuffer)
    }
  }
}
