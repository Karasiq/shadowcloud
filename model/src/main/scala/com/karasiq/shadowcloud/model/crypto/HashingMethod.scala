package com.karasiq.shadowcloud.model.crypto

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.SerializedProps

@SerialVersionUID(0L)
final case class HashingMethod(algorithm: String, config: SerializedProps = SerializedProps.empty,
                               provider: String = "") extends CryptoMethod {

  @transient
  private[this] val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

  override def hashCode(): Int = {
    _hashCode
  }

  override def toString: String = {
    if (CryptoMethod.isNoOpMethod(this)) {
      "HashingMethod.none"
    } else {
      s"HashingMethod(${if (provider.isEmpty) algorithm else provider + ":" + algorithm}${if (config.isEmpty) "" else ", " + config})"
    }
  }
}

object HashingMethod {
  val none = HashingMethod("")
  val default = HashingMethod("Blake2b")
}