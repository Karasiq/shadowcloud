package com.karasiq.shadowcloud.config

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, HashingMethod, SignMethod}

private[shadowcloud] object CryptoProps extends ConfigImplicits {
  def hashing(config: Config): HashingMethod = {
    val algorithm = config.getString("algorithm")
    val provider  = config.withDefault("", _.getString("provider"))
    val props     = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "provider"))
    HashingMethod(algorithm, props, provider)
  }

  def encryption(config: Config): EncryptionMethod = {
    val algorithm = config.getString("algorithm")
    val keySize   = config.withDefault(256, _.getInt("key-size"))
    val provider  = config.withDefault("", _.getString("provider"))
    val props     = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "key-size", "provider"))
    EncryptionMethod(algorithm, keySize, props, provider)
  }

  def signing(config: Config): SignMethod = {
    val algorithm = config.getString("algorithm")
    val hashing   = CryptoProps.hashing(config.getConfigOrRef("hashing"))
    val keySize   = config.withDefault(256, _.getInt("key-size"))
    val provider  = config.withDefault("", _.getString("provider"))
    val props     = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "hashing", "key-size", "provider"))
    SignMethod(algorithm, hashing, keySize, props, provider)
  }

  def keyGeneration(props: SerializedProps): (Option[EncryptionMethod], Option[SignMethod]) = {
    val encConfig = ConfigProps.toConfig(props)

    val encMethod  = encConfig.optional(_.getConfig("encryption")).map(this.encryption)
    val signMethod = encConfig.optional(_.getConfig("signing")).map(this.signing)

    (encMethod, signMethod)
  }

  private[this] def withoutPaths(config: Config, paths: String*): Config = {
    paths.foldLeft(config)(_.withoutPath(_))
  }
}
