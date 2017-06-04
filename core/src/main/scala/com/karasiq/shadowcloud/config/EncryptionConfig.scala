package com.karasiq.shadowcloud.config

import scala.language.postfixOps

import com.typesafe.config.ConfigException

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.EncryptionMethod

private[shadowcloud] case class EncryptionConfig(chunks: EncryptionMethod, index: EncryptionMethod, keys: EncryptionMethod)

private[shadowcloud] object EncryptionConfig extends ConfigImplicits {
  def apply(config: Config): EncryptionConfig = {
    EncryptionConfig(
      getEncryptionMethod(config, "chunks"),
      getEncryptionMethod(config, "index"),
      getEncryptionMethod(config, "keys")
    )
  }

  private[this] def getEncryptionMethod(config: Config, path: String): EncryptionMethod = {
    try {
      CryptoProps.encryption(config.getConfigOrRef(path))
    } catch { case _: ConfigException.Missing ⇒
      val alg = config.getString(path)
      EncryptionMethod(alg)
    }
  }
}