package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits

private[shadowcloud] case class AppConfig(crypto: CryptoConfig, storage: StoragesConfig, parallelism: ParallelismConfig)

private[shadowcloud] object AppConfig extends ConfigImplicits {
  def apply(config: Config): AppConfig = {
    AppConfig(
      CryptoConfig(config.getConfig("crypto")),
      StoragesConfig(config.getConfig("storage")),
      ParallelismConfig(config.getConfig("parallelism"))
    )
  }
}