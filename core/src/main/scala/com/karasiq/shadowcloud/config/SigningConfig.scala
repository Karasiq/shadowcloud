package com.karasiq.shadowcloud.config

import scala.language.postfixOps

import com.typesafe.config.ConfigException

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{HashingMethod, SignMethod}

case class SigningConfig(index: SignMethod)

object SigningConfig extends ConfigImplicits {
  def apply(config: Config): SigningConfig = {
    SigningConfig(
      getSignMethod(config, "index")
    )
  }

  private[this] def getSignMethod(config: Config, path: String): SignMethod = {
    try {
      CryptoProps.sign(config.getConfigOrRef(path))
    } catch { case _: ConfigException.Missing ⇒
      val alg = config.getString(path)
      SignMethod(alg, HashingMethod.default)
    }
  }
}