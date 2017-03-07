package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps

import scala.language.postfixOps

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
  val default = HashingMethod("SHA1")
}