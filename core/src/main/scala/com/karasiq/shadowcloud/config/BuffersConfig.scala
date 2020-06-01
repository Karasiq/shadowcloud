package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

case class BuffersConfig(rootConfig: Config, readChunks: Long, repair: Long) extends WrappedConfig

object BuffersConfig extends WrappedConfigFactory[BuffersConfig] {
  override def apply(config: Config): BuffersConfig = {
    BuffersConfig(
      config,
      config.getMemorySize("read-chunks").toBytes,
      config.getMemorySize("repair").toBytes
    )
  }
}
