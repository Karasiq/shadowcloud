package com.karasiq.shadowcloud.crypto.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionParameters, StreamEncryptionModule}

import scala.language.postfixOps

private[crypto] final class NoOpEncryptionModule extends StreamEncryptionModule {
  def createParameters(): EncryptionParameters = {
    EncryptionParameters.empty
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    parameters
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    // Nothing
  }

  def process(data: ByteString): ByteString = {
    data
  }

  def finish(): ByteString = {
    ByteString.empty
  }

  override def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    data
  }

  override def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    data
  }
}