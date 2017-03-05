package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps

import scala.language.postfixOps

case class HashingMethod(algorithm: String, stream: Boolean = false, provider: String = "", config: SerializedProps = SerializedProps.empty)

object HashingMethod {
  val none = HashingMethod("")
  val default = HashingMethod("SHA1")
}