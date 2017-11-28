package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper

final case class StorageConfig(rootConfig: Config,
                               chunkKey: ChunkKeyMapper,
                               healthCheckInterval: FiniteDuration,
                               index: StorageIndexConfig,
                               chunkIO: StorageChunkIOConfig) extends WrappedConfig

object StorageConfig extends WrappedConfigFactory[StorageConfig] with ConfigImplicits {
  private[this] def getConfigForId(storageId: StorageId, rootConfig: Config): Config = {
    rootConfig.getConfigOrRef(s"storages.$storageId")
      .withFallback(rootConfig.getConfig("default-storage"))
  }

  def forId(storageId: StorageId, rootConfig: Config): StorageConfig = {
    val config = getConfigForId(storageId, rootConfig)
    apply(config)
  }

  def forProps(storageId: StorageId, props: StorageProps, rootConfig: Config): StorageConfig = {
    val config = props.rootConfig.getConfigOrRef("custom-config")
      .withFallback(getConfigForId(storageId, rootConfig))
    apply(config)
  }

  def apply(config: Config): StorageConfig = {
    StorageConfig(
      config,
      ChunkKeyMapper.forName(config.getString("chunk-key"), config.getConfigIfExists("chunk-key-config")),
      config.getFiniteDuration("health-check-interval"),
      StorageIndexConfig(config.getConfig("index")),
      StorageChunkIOConfig(config.getConfig("chunk-io"))
    )
  }
}