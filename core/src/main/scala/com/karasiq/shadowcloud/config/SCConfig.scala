package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

private[shadowcloud] case class SCConfig(rootConfig: Config, crypto: CryptoConfig, storage: StoragesConfig, parallelism: ParallelismConfig)

private[shadowcloud] object SCConfig extends ConfigImplicits {
  def apply(config: Config): SCConfig = {
    SCConfig(
      config,
      CryptoConfig(config.getConfig("crypto")),
      StoragesConfig(config.getConfig("storage")),
      ParallelismConfig(config.getConfig("parallelism"))
    )
  }
}