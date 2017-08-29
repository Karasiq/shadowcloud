package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.SerializedProps

@SerialVersionUID(0L)
case class EncryptionMethod(algorithm: String, keySize: Int = 256,
                            config: SerializedProps = SerializedProps.empty,
                            provider: String = "") extends CryptoMethod {

  override def toString: String = {
    if (CryptoMethod.isNoOpMethod(this)) {
      "EncryptionMethod.none"
    } else {
      s"EncryptionMethod(${if (provider.isEmpty) algorithm else provider + ":" + algorithm}, $keySize bits${if (config.isEmpty) "" else ", " + config})"
    }
  }
}

object EncryptionMethod {
  val none = EncryptionMethod("", 0)
  val default = EncryptionMethod("ChaCha20", 256)
}