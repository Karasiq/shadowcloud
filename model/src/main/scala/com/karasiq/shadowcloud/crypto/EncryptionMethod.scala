package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps
import com.typesafe.config.Config

import scala.language.postfixOps

case class EncryptionMethod(algorithm: String, keySize: Int, provider: String = "", config: SerializedProps = SerializedProps.empty)

object EncryptionMethod {
  val none = EncryptionMethod("", 0)
  val default = EncryptionMethod("AES", 256)

  def apply(config: Config): EncryptionMethod = {
    val algorithm = config.getString("algorithm")
    val keySize = config.getInt("key-size")
    val provider = Option(config.getString("provider")).getOrElse("")
    val props = if (config.entrySet().size() <= 3) SerializedProps.empty else SerializedProps.fromConfig(config)
    EncryptionMethod(algorithm, keySize, provider, props)
  }
}