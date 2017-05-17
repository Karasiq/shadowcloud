package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class RegionConfig(dataReplicationFactor: Int, indexReplicationFactor: Int)

object RegionConfig extends ConfigImplicits {
  def forId(regionId: String, rootConfig: Config): RegionConfig = {
    val cfgObject = rootConfig.getConfigOrRef(s"regions.$regionId")
      .withFallback(rootConfig.getConfig("default-region"))
    apply(cfgObject)
  }

  def apply(config: Config): RegionConfig = {
    RegionConfig(
      config.getInt("data-replication-factor"),
      config.getInt("index-replication-factor")
    )
  }
}