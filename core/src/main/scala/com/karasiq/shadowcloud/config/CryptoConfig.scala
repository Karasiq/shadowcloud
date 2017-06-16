package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.keys.KeyProvider
import com.karasiq.shadowcloud.config.passwords.PasswordProvider
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.providers.CryptoProvider

private[shadowcloud] case class CryptoConfig(hashing: HashingConfig, encryption: EncryptionConfig,
                                             signing: SigningConfig, providers: ProvidersConfig[CryptoProvider],
                                             keyProvider: Class[KeyProvider], passwordProvider: Class[PasswordProvider])

private[shadowcloud] object CryptoConfig extends ConfigImplicits {
  def apply(config: Config): CryptoConfig = {
    CryptoConfig(
      HashingConfig(config.getConfig("hashing")),
      EncryptionConfig(config.getConfig("encryption")),
      SigningConfig(config.getConfig("signing")),
      ProvidersConfig(config.getConfig("providers")),
      config.getClass("key-provider"),
      config.getClass("password-provider")
    )
  }
}