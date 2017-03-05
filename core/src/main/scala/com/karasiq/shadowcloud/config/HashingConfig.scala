package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.HashingMethod

import scala.language.postfixOps

case class HashingConfig(chunks: HashingMethod, files: HashingMethod)

object HashingConfig extends ConfigImplicits {
  def apply(config: Config): HashingConfig = {
    HashingConfig(
      CryptoProps.hashing(config.getConfigOrRef("chunks")),
      CryptoProps.hashing(config.getConfigOrRef("files"))
    )
  }
}
