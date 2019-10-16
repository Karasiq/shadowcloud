package com.karasiq.shadowcloud.config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.providers.{CryptoProvider, KeyProvider}
import com.typesafe.config.Config

private[shadowcloud] case class CryptoConfig(
    rootConfig: Config,
    hashing: HashingConfig,
    encryption: EncryptionConfig,
    signing: SigningConfig,
    providers: ProvidersConfig[CryptoProvider],
    keyProvider: Class[KeyProvider]
) extends WrappedConfig

private[shadowcloud] object CryptoConfig extends WrappedConfigFactory[CryptoConfig] with ConfigImplicits {
  def apply(config: Config): CryptoConfig = {
    CryptoConfig(
      config,
      HashingConfig(config.getConfig("hashing")),
      EncryptionConfig(config.getConfig("encryption")),
      SigningConfig(config.getConfig("signing")),
      ProvidersConfig.withType[CryptoProvider](config.getConfig("providers")),
      config.getClass("key-provider")
    )
  }
}
