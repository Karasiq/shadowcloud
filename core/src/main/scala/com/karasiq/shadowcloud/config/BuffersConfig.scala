package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

case class BuffersConfig(rootConfig: Config, storageWrite: Int, storageRead: Int) extends WrappedConfig

object BuffersConfig extends ConfigImplicits with WrappedConfigFactory[BuffersConfig] {
  def apply(config: Config): BuffersConfig = {
    BuffersConfig(
      config,
      config.getInt("storage-write"),
      config.getInt("storage-read")
    )
  }
}
