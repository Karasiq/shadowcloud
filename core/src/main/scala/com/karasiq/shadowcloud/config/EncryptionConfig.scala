package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.EncryptionMethod
import com.typesafe.config.ConfigException

import scala.language.postfixOps

private[shadowcloud] case class EncryptionConfig(chunks: EncryptionMethod)

private[shadowcloud] object EncryptionConfig extends ConfigImplicits {
  def apply(config: Config): EncryptionConfig = {
    EncryptionConfig(getEncryptionMethod(config, "chunks"))
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