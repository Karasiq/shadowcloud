package com.karasiq.shadowcloud.config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.crypto.EncryptionMethod
import com.typesafe.config.{Config, ConfigException}

private[shadowcloud] case class EncryptionConfig(
    rootConfig: Config,
    chunks: EncryptionMethod,
    index: EncryptionMethod,
    keys: EncryptionMethod,
    maxKeyReuse: Int
) extends WrappedConfig

private[shadowcloud] object EncryptionConfig extends WrappedConfigFactory[EncryptionConfig] with ConfigImplicits {
  def apply(config: Config): EncryptionConfig = {
    EncryptionConfig(
      config,
      getEncryptionMethod(config, "chunks"),
      getEncryptionMethod(config, "index"),
      getEncryptionMethod(config, "keys"),
      config.getInt("max-key-reuse")
    )
  }

  private[this] def getEncryptionMethod(config: Config, path: String): EncryptionMethod = {
    try {
      CryptoProps.encryption(config.getConfigOrRef(path))
    } catch {
      case _: ConfigException.Missing ⇒
        val alg = config.getString(path)
        EncryptionMethod(alg)
    }
  }
}
