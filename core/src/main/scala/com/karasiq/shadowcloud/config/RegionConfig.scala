package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.storage.replication.StorageSelector

case class RegionConfig(storageSelector: Class[StorageSelector], dataReplicationFactor: Int,
                        indexReplicationFactor: Int, gcAutoDelete: Boolean)

object RegionConfig extends ConfigImplicits {
  def forId(regionId: String, rootConfig: Config): RegionConfig = {
    val cfgObject = rootConfig.getConfigOrRef(s"regions.$regionId")
      .withFallback(rootConfig.getConfig("default-region"))
    apply(cfgObject)
  }

  def apply(config: Config): RegionConfig = {
    RegionConfig(
      config.getClass("storage-selector"),
      config.getInt("data-replication-factor"),
      config.getInt("index-replication-factor"),
      config.getBoolean("gc-auto-delete")
    )
  }
}