package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.karasiq.shadowcloud.config.utils.{ChunkKeyExtractor, ConfigImplicits}

case class StorageConfig(syncInterval: FiniteDuration, indexCompactThreshold: Int, chunkKey: ChunkKeyExtractor)

object StorageConfig extends ConfigImplicits {
  def forId(storageId: String, rootConfig: Config): StorageConfig = {
    apply(rootConfig.getConfigOrRef(s"storages.$storageId")
      .withFallback(rootConfig.getConfig("default-storage")))
  }

  def apply(config: Config): StorageConfig = {
    StorageConfig(
      config.getFiniteDuration("sync-interval"),
      config.getInt("index-compact-threshold"),
      ChunkKeyExtractor.fromString(config.getString("chunk-key"))
    )
  }
}