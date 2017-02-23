package com.karasiq.shadowcloud.crypto

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.internal.{AESGCMEncryptionModule, PlainEncryptionModule}

import scala.language.postfixOps

trait EncryptionModule {
  def createParameters(): EncryptionParameters
  def updateParameters(parameters: EncryptionParameters): EncryptionParameters
  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit
  def process(data: ByteString): ByteString
  def finish(): ByteString

  // One pass functions
  def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    init(encrypt = true, parameters)
    process(data) ++ finish()
  }

  def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    init(encrypt = false, parameters)
    process(data) ++ finish()
  }
}

object EncryptionModule {
  import EncryptionMethod._
  val plain: EncryptionModule = new PlainEncryptionModule

  def apply(method: EncryptionMethod): EncryptionModule = method match {
    case Plain ⇒
      plain
      
    case AES("GCM", bits @ (128 | 256)) ⇒
      new AESGCMEncryptionModule(bits)

    case aes: AES ⇒
      throw new IllegalArgumentException(s"AES mode unsupported: $aes")
  }
}