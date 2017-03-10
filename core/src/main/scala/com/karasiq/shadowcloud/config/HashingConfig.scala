package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.HashingMethod
import com.typesafe.config.ConfigException

import scala.language.postfixOps

private[shadowcloud] case class HashingConfig(chunks: HashingMethod, chunksEncrypted: HashingMethod,
                         files: HashingMethod, filesEncrypted: HashingMethod)

private[shadowcloud] object HashingConfig extends ConfigImplicits {
  def apply(config: Config): HashingConfig = {
    HashingConfig(
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
