package com.karasiq.shadowcloud.crypto

import akka.util.ByteString
import com.karasiq.shadowcloud.utils.Utils

import scala.language.postfixOps

case class EncryptionParameters(method: EncryptionMethod, key: ByteString, iv: ByteString) {
  override def toString: String = {
    if (this.eq(EncryptionParameters.empty)) {
      "EncryptionParameters.empty"
    } else {
      s"EncryptionParameters($method, key = ${Utils.toHexString(key)}, iv = ${Utils.toHexString(iv)})"
    }
  }
}

object EncryptionParameters {
  // No encryption
  val empty = EncryptionParameters(EncryptionMethod.Plain, ByteString.empty, ByteString.empty)
}