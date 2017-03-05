package com.karasiq.shadowcloud.crypto

import com.karasiq.shadowcloud.config.SerializedProps

import scala.language.postfixOps

case class EncryptionMethod(algorithm: String, keySize: Int, stream: Boolean = false,
                            provider: String = "", config: SerializedProps = SerializedProps.empty)

object EncryptionMethod {
  val none = EncryptionMethod("", 0)
  val default = EncryptionMethod("AES", 128)
}