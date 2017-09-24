package com.karasiq.shadowcloud.config

import scala.concurrent.duration.FiniteDuration

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.ChunkKeyMapper

case class StorageConfig(rootConfig: Config, syncInterval: FiniteDuration,
                         indexCompactThreshold: Int, indexSnapshotThreshold: Int,
                         chunkKey: ChunkKeyMapper) extends WrappedConfig

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
      config.getFiniteDuration("sync-interval"),
      config.getInt("index-compact-threshold"),
      config.getInt("index-snapshot-threshold"),
      ChunkKeyMapper.forName(config.getString("chunk-key"), config.getConfigIfExists("chunk-key-config"))
    )
  }
}