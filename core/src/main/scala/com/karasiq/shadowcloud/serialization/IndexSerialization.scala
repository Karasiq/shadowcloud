package com.karasiq.shadowcloud.serialization

import akka.util.ByteString
import play.api.libs.json.Json

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.model.crypto.EncryptionParameters
import com.karasiq.shadowcloud.serialization.boopickle.SCBooPickleEncoders
import com.karasiq.shadowcloud.serialization.json.SCJsonEncoders
import com.karasiq.shadowcloud.serialization.protobuf.index.SerializedIndexData

private[shadowcloud] trait IndexSerialization {
  def wrapIndexFrame(data: IndexData): SerializedIndexData
  def unwrapIndexFrame(data: SerializedIndexData): IndexData
  def wrapKey(parameters: EncryptionParameters): ByteString
  def unwrapKey(data: ByteString): EncryptionParameters
}

private[shadowcloud] object IndexSerialization {
  def apply()(implicit sc: ShadowCloudExtension): IndexSerialization = {
    new DefaultIndexSerialization()
  }
}

private[shadowcloud] final class DefaultIndexSerialization(implicit sc: ShadowCloudExtension) extends IndexSerialization  {
  private[this] val JsonPrefix = ByteString("{")
  private[this] val BooPicklePrefix = ByteString("__boopickle")

  def wrapIndexFrame(data: IndexData): SerializedIndexData = {
    val format = sc.config.serialization.indexFormat
    val bytes = format match {
      case "default" | "" ⇒
        sc.serialization.toBytes(data)

      case "boopickle" ⇒
        import SCBooPickleEncoders._
        ByteString(Pickle.intoBytes(data))

      case "json" ⇒
        import SCJsonEncoders._
        ByteString(Json.toBytes(Json.toJson(data)))
    }
    SerializedIndexData(format, bytes)
  }

  def unwrapIndexFrame(data: SerializedIndexData): IndexData = data.format match {
    case "default" | "" ⇒
      sc.serialization.fromBytes[IndexData](data.data)

    case "boopickle" ⇒
      import SCBooPickleEncoders._
      Unpickle[IndexData].fromBytes(data.data.toByteBuffer)

    case "json" ⇒
      import SCJsonEncoders._
      Json.parse(data.data.toArray).as[IndexData]
  }

  def wrapKey(parameters: EncryptionParameters): ByteString = {
    sc.config.serialization.indexFormat match {
      case "default" | "" ⇒
        sc.serialization.toBytes(parameters)

      case "boopickle" ⇒
        import SCBooPickleEncoders._
        BooPicklePrefix ++ ByteString(Pickle.intoBytes(parameters))

      case "json" ⇒
        import SCJsonEncoders._
        ByteString(Json.toBytes(Json.toJson(parameters)))
    }
  }

  def unwrapKey(data: ByteString): EncryptionParameters = {
    if (data.startsWith(JsonPrefix)) {
      import SCJsonEncoders._
      Json.parse(data.toArray).as[EncryptionParameters]
    } else if (data.startsWith(BooPicklePrefix)) {
      import SCBooPickleEncoders._
      Unpickle[EncryptionParameters].fromBytes(data.drop(BooPicklePrefix.length).toByteBuffer)
    } else {
      sc.serialization.fromBytes[EncryptionParameters](data)
    }
  }
}
