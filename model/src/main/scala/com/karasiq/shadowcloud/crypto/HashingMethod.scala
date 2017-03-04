package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps
import com.typesafe.config.Config

import scala.language.postfixOps

case class HashingMethod(algorithm: String, provider: String = "", config: SerializedProps = SerializedProps.empty)

object HashingMethod {
  val none = HashingMethod("")
  val default = HashingMethod("SHA1")

  def apply(config: Config): HashingMethod = {
    val algorithm = config.getString("algorithm")
    val provider = Option(config.getString("provider")).getOrElse("")
    val props = if (config.entrySet().size() <= 2) SerializedProps.empty else SerializedProps.fromConfig(config)
    HashingMethod(algorithm, provider, props)
  }
}