package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.SerializedProps

case class HashingMethod(algorithm: String, stream: Boolean = false, provider: String = "",
                         config: SerializedProps = SerializedProps.empty) extends CryptoMethod {
  override def toString: String = {
    if (algorithm.isEmpty) {
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