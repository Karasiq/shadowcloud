package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.common.configs.ConfigImplicits

private[shadowcloud] case class ChunksConfig(rootConfig: Config, chunkSize: Int) extends WrappedConfig

private[shadowcloud] object ChunksConfig extends WrappedConfigFactory[ChunksConfig] with ConfigImplicits {
  def apply(config: Config): ChunksConfig = {
    ChunksConfig(
      config,
      config.getBytesInt("chunk-size")
    )
  }
}
