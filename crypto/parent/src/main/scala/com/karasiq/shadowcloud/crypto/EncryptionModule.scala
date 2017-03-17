package com.karasiq.shadowcloud.crypto

import akka.util.ByteString

import scala.language.postfixOps

trait EncryptionModule {
  def createParameters(): EncryptionParameters
  def updateParameters(parameters: EncryptionParameters): EncryptionParameters
  def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString
  def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString
}

trait StreamEncryptionModule extends EncryptionModule {
  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit
  def process(data: ByteString): ByteString
  def finish(): ByteString

  // One pass functions
  override def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    init(encrypt = true, parameters)
    process(data) ++ finish()
  }

  override def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    init(encrypt = false, parameters)
    process(data) ++ finish()
  }
}