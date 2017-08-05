package com.karasiq.shadowcloud.config

import com.typesafe.config.Config

import com.karasiq.shadowcloud.config.passwords.PasswordProvider
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.providers.{CryptoProvider, KeyProvider}

private[shadowcloud] case class CryptoConfig(rootConfig: Config, hashing: HashingConfig, encryption: EncryptionConfig,
                                             signing: SigningConfig, providers: ProvidersConfig[CryptoProvider],
                                             keyProvider: Class[KeyProvider], passwordProvider: Class[PasswordProvider]) extends WrappedConfig

private[shadowcloud] object CryptoConfig extends WrappedConfigFactory[CryptoConfig] with ConfigImplicits {
  def apply(config: Config): CryptoConfig = {
    CryptoConfig(
      config,
      HashingConfig(config.getConfig("hashing")),
      EncryptionConfig(config.getConfig("encryption")),
      SigningConfig(config.getConfig("signing")),
      ProvidersConfig.withType[CryptoProvider](config.getConfig("providers")),
      config.getClass("key-provider"),
      config.getClass("password-provider")
    )
  }
}