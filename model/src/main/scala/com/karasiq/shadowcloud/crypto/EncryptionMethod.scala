package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps

import scala.language.postfixOps

case class EncryptionMethod(algorithm: String, keySize: Int, stream: Boolean = false,
                            provider: String = "", config: SerializedProps = SerializedProps.empty) extends CryptoMethod {
  override def toString: String = {
    if (algorithm.isEmpty) {
      "EncryptionMethod.none"
    } else {
      s"EncryptionMethod(${if (provider.isEmpty) algorithm else provider + ":" + algorithm}, $keySize bits${if (config.isEmpty) "" else ", " + config})"
    }
  }
}

object EncryptionMethod {
  val none = EncryptionMethod("", 0)
  val default = EncryptionMethod("AES", 256)
}