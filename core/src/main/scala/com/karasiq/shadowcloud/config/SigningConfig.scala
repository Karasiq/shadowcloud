package com.karasiq.shadowcloud.config

import scala.language.postfixOps

import com.typesafe.config.{Config, ConfigException}

import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.model.crypto.{HashingMethod, SignMethod}

case class SigningConfig(rootConfig: Config, index: SignMethod) extends WrappedConfig

object SigningConfig extends WrappedConfigFactory[SigningConfig] with ConfigImplicits {
  def apply(config: Config): SigningConfig = {
    SigningConfig(
      config,
      getSignMethod(config, "index")
    )
  }

  private[this] def getSignMethod(config: Config, path: String): SignMethod = {
    try {
      CryptoProps.signing(config.getConfigOrRef(path))
    } catch { case _: ConfigException.Missing ⇒
      val alg = config.getString(path)
      SignMethod(alg, HashingMethod.default)
    }
  }
}