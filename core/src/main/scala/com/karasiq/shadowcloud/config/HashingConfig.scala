package com.karasiq.shadowcloud.config

import scala.language.postfixOps

import com.typesafe.config.{Config, ConfigException}

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.crypto.HashingMethod

private[shadowcloud] case class HashingConfig(rootConfig: Config, chunks: HashingMethod,
                                              chunksEncrypted: HashingMethod, files: HashingMethod,
                                              filesEncrypted: HashingMethod) extends WrappedConfig

private[shadowcloud] object HashingConfig extends WrappedConfigFactory[HashingConfig] with ConfigImplicits {
  def apply(config: Config): HashingConfig = {
    HashingConfig(
      config,
      getHashingMethod(config, "chunks"),
      getHashingMethod(config, "chunks-encrypted"),
      getHashingMethod(config, "files"),
      getHashingMethod(config, "files-encrypted")
    )
  }

  private[this] def getHashingMethod(config: Config, path: String): HashingMethod = {
    try {
      CryptoProps.hashing(config.getConfigOrRef(path))
    } catch { case _: ConfigException.Missing ⇒
      val alg = config.getString(path)
      HashingMethod(alg)
    }
  }
}
