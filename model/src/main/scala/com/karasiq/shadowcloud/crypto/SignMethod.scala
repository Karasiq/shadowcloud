package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps

case class SignMethod(algorithm: String, hashingMethod: HashingMethod, keySize: Int = 256, stream: Boolean = false,
                      provider: String = "", config: SerializedProps = SerializedProps.empty) extends CryptoMethod {
  override def toString: String = {
    if (CryptoMethod.isNoOpMethod(this)) {
      "SignMethod.none"
    } else {
      s"SignMethod(${if (provider.isEmpty) algorithm else provider + ":" + algorithm}, $hashingMethod, $keySize bits${if (config.isEmpty) "" else ", " + config})"
    }
  }
}

object SignMethod {
  val none = SignMethod("", HashingMethod.none)
}
