package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.storage.replication.StorageSelector

@SerialVersionUID(0L)
final case class RegionConfig(rootConfig: Config,
                              storageSelector: Class[StorageSelector],
                              dataReplicationFactor: Int,
                              indexReplicationFactor: Int,
                              garbageCollector: GCConfig) extends WrappedConfig

object RegionConfig extends WrappedConfigFactory[RegionConfig] with ConfigImplicits {
  def forId(regionId: RegionId, rootConfig: Config): RegionConfig = {
    val cfgObject = rootConfig.getConfigOrRef(s"regions.$regionId")
      .withFallback(rootConfig.getConfig("default-region"))
    apply(cfgObject)
  }

  def apply(config: Config): RegionConfig = {
    RegionConfig(
      config,
      config.getClass("storage-selector"),
      config.getInt("data-replication-factor"),
      config.getInt("index-replication-factor"),
      GCConfig(config.getConfigIfExists("garbage-collector"))
    )
  }
}