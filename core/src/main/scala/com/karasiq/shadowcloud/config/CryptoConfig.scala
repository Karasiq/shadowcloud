package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.providers.CryptoProvider

case class CryptoConfig(hashing: HashingConfig, encryption: EncryptionConfig, providers: ProvidersConfig[CryptoProvider])

object CryptoConfig extends ConfigImplicits {
  def apply(config: Config): CryptoConfig = {
    CryptoConfig(
      HashingConfig(config.getConfig("hashing")),
      EncryptionConfig(config.getConfig("encryption")),
      ProvidersConfig(config.getConfig("providers"))
    )
  }
}