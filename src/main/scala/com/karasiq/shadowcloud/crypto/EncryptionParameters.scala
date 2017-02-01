package com.karasiq.shadowcloud.crypto

import akka.util.ByteString

import scala.language.postfixOps

case class EncryptionParameters(method: EncryptionMethod, key: ByteString, iv: ByteString)

object EncryptionParameters {
  // No encryption
  val empty = EncryptionParameters(EncryptionMethod.Plain, ByteString.empty, ByteString.empty)
}