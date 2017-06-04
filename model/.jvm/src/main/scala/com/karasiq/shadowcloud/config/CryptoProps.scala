package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, HashingMethod, SignMethod}

private[shadowcloud] object CryptoProps extends ConfigImplicits {
  def hashing(config: Config): HashingMethod = {
    val algorithm = config.getString("algorithm")
    val stream = config.withDefault(false, _.getBoolean("stream"))
    val provider = config.withDefault("", _.getString("provider"))
    val props = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "stream", "provider"))
    HashingMethod(algorithm, stream, provider, props)
  }

  def encryption(config: Config): EncryptionMethod = {
    val algorithm = config.getString("algorithm")
    val keySize = config.withDefault(256, _.getInt("key-size"))
    val stream = config.withDefault(false, _.getBoolean("stream"))
    val provider = config.withDefault("", _.getString("provider"))
    val props = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "key-size", "stream", "provider"))
    EncryptionMethod(algorithm, keySize, stream, provider, props)
  }

  def sign(config: Config): SignMethod = {
    val algorithm = config.getString("algorithm")
    val hashing = CryptoProps.hashing(config.getConfigOrRef("hashing"))
    val keySize = config.withDefault(256, _.getInt("key-size"))
    val stream = config.withDefault(false, _.getBoolean("stream"))
    val provider = config.withDefault("", _.getString("provider"))
    val props = ConfigProps.fromConfig(withoutPaths(config, "algorithm", "hashing", "key-size", "stream", "provider"))
    SignMethod(algorithm, hashing, keySize, stream, provider, props)
  }

  private[this] def withoutPaths(config: Config, paths: String*): Config = {
    paths.foldLeft(config)(_.withoutPath(_))
  }
}
