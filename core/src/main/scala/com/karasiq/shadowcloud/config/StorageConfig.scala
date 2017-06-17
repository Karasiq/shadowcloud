package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.karasiq.shadowcloud.config.utils.{ChunkKeyExtractor, ConfigImplicits}
import com.karasiq.shadowcloud.serialization.protobuf.index.SerializedIndexData

case class StorageConfig(syncInterval: FiniteDuration, indexCompactThreshold: Int,
                         indexCompression: SerializedIndexData.Compression,
                         chunkKey: ChunkKeyExtractor)

object StorageConfig extends ConfigImplicits {
  def forId(storageId: String, rootConfig: Config): StorageConfig = {
    apply(rootConfig.getConfigOrRef(s"storages.$storageId")
      .withFallback(rootConfig.getConfig("default-storage")))
  }

  def apply(config: Config): StorageConfig = {
    StorageConfig(
      config.getFiniteDuration("sync-interval"),
      config.getInt("index-compact-threshold"),
      toCompressionType(config.getString("index-compression")),
      ChunkKeyExtractor.fromString(config.getString("chunk-key"))
    )
  }

  private[this] def toCompressionType(str: String): SerializedIndexData.Compression = str match {
    case "gzip" ⇒
      SerializedIndexData.Compression.GZIP

    case "none" ⇒
      SerializedIndexData.Compression.NONE

    case _ ⇒
      throw new IllegalArgumentException("Invalid compression type: " + str)
  }
}