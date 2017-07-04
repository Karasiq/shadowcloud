package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

private[shadowcloud] case class SCConfig(rootConfig: Config, crypto: CryptoConfig, storage: StoragesConfig,
                                         metadata: MetadataConfig, parallelism: ParallelismConfig) extends WrappedConfig

private[shadowcloud] object SCConfig extends WrappedConfigFactory[SCConfig] with ConfigImplicits {
  def apply(config: Config): SCConfig = {
    SCConfig(
      config,
      CryptoConfig(config.getConfig("crypto")),
      StoragesConfig(config.getConfig("storage")),
      MetadataConfig(config.getConfig("metadata")),
      ParallelismConfig(config.getConfig("parallelism"))
    )
  }
}