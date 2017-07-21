package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class QueuesConfig(rootConfig: Config, storageWrite: Int, storageRead: Int, regionRepair: Int) extends WrappedConfig

object QueuesConfig extends ConfigImplicits with WrappedConfigFactory[QueuesConfig] {
  def apply(config: Config): QueuesConfig = {
    QueuesConfig(
      config,
      config.getInt("storage-write"),
      config.getInt("storage-read"),
      config.getInt("region-repair")
    )
  }
}
