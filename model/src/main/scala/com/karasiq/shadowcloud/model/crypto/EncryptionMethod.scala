package com.karasiq.shadowcloud.model.crypto

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.SerializedProps

@SerialVersionUID(0L)
final case class EncryptionMethod(algorithm: String, keySize: Int = 256,
                                  config: SerializedProps = SerializedProps.empty,
                                  provider: String = "") extends CryptoMethod {

  @transient
  private[this] val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

  override def hashCode(): Int = {
    _hashCode
  }

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