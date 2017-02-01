package com.karasiq.shadowcloud.crypto

import akka.util.ByteString

import scala.language.postfixOps

trait EncryptionModule {
  def createParameters(): EncryptionParameters
  def updateParameters(parameters: EncryptionParameters): EncryptionParameters
  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit
  def process(data: ByteString): ByteString
  def finish(): ByteString
}

object EncryptionModule {
  import EncryptionMethod._

  def apply(method: EncryptionMethod): EncryptionModule = method match {
    case Plain ⇒
      PlainEncryptionModule
      
    case AES("GCM", bits @ (128 | 256)) ⇒
      new AESGCMEncryptionModule(bits)

    case aes: AES ⇒
      throw new IllegalArgumentException(s"AES mode unsupported: $aes")
  }
}