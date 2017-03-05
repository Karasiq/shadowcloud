package com.karasiq.shadowcloud.config


import com.karasiq.shadowcloud.config.utils.{ChunkKeyExtractor, ConfigImplicits}
import com.karasiq.shadowcloud.providers.StorageProvider

import scala.language.postfixOps

case class StorageConfig(replicationFactor: Int, chunkKey: ChunkKeyExtractor, providers: ProvidersConfig[StorageProvider])

object StorageConfig extends ConfigImplicits {
  def apply(config: Config): StorageConfig = {
    StorageConfig(
      config.getInt("replication-factor"),
      ChunkKeyExtractor.fromString(config.getString("chunk-key")),
      ProvidersConfig(config.getConfig("providers"))
    )
  }
}