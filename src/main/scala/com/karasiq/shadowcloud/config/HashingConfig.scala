package com.karasiq.shadowcloud.config

import com.karasiq.shadowcloud.crypto.HashingMethod

import scala.language.postfixOps

case class HashingConfig(chunks: HashingMethod, files: HashingMethod)

object HashingConfig extends ConfigImplicits {
  private[this] def getMethod(config: Config, key: String): HashingMethod = {
    val alg = config.getString(key)
    if (alg == HashingMethod.default.algorithm)
      HashingMethod.default
    else
      HashingMethod(alg)
  }

  def apply(config: Config): HashingConfig = {
    HashingConfig(getMethod(config, "chunks"), getMethod(config, "files"))
  }
}
