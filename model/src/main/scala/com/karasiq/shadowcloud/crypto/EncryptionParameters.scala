package com.karasiq.shadowcloud.crypto

import akka.util.ByteString
import com.karasiq.shadowcloud.utils.HexString

import scala.language.postfixOps

case class EncryptionParameters(method: EncryptionMethod, key: ByteString, iv: ByteString) {
  override def toString: String = {
    if (this.eq(EncryptionParameters.empty)) {
      "EncryptionParameters.empty"
    } else {
      s"EncryptionParameters($method, key = ${HexString.encode(key)}, iv = ${HexString.encode(iv)})"
    }
  }
}

object EncryptionParameters {
  // No encryption
  val empty = EncryptionParameters(EncryptionMethod.none, ByteString.empty, ByteString.empty)
}