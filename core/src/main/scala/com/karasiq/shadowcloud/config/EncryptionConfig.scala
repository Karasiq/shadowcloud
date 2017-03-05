package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.EncryptionMethod

import scala.language.postfixOps

case class EncryptionConfig(chunks: EncryptionMethod)

object EncryptionConfig extends ConfigImplicits {
  def apply(config: Config): EncryptionConfig = {
    EncryptionConfig(
      CryptoProps.encryption(config.getConfigOrRef("chunks"))
    )
  }
}