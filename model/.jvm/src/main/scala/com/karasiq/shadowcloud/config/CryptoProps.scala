package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, HashingMethod, SignMethod}

private[shadowcloud] object CryptoProps extends ConfigImplicits {
  def hashing(config: Config): HashingMethod = {
    val algorithm = config.getString("algorithm")
    val provider = config.withDefault("", _.getString("provider"))
    val props = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "provider"))
    HashingMethod(algorithm, props, provider)
  }

  def encryption(config: Config): EncryptionMethod = {
    val algorithm = config.getString("algorithm")
    val keySize = config.withDefault(256, _.getInt("key-size"))
    val provider = config.withDefault("", _.getString("provider"))
    val props = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "key-size", "provider"))
    EncryptionMethod(algorithm, keySize, props, provider)
  }

  def sign(config: Config): SignMethod = {
    val algorithm = config.getString("algorithm")
    val hashing = CryptoProps.hashing(config.getConfigOrRef("hashing"))
    val keySize = config.withDefault(256, _.getInt("key-size"))
    val provider = config.withDefault("", _.getString("provider"))
    val props = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "hashing", "key-size", "provider"))
    SignMethod(algorithm, hashing, keySize, props, provider)
  }

  private[this] def withoutPaths(config: Config, paths: String*): Config = {
    paths.foldLeft(config)(_.withoutPath(_))
  }
}
