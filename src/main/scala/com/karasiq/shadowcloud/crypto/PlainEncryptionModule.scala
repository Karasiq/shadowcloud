package com.karasiq.shadowcloud.crypto

import akka.util.ByteString

import scala.language.postfixOps

class PlainEncryptionModule extends EncryptionModule {
  def createParameters() = {
    EncryptionParameters.empty
  }

  def updateParameters(parameters: EncryptionParameters) = {
    parameters
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters) = {
    // Nothing
  }

  def process(data: ByteString) = {
    data
  }

  def finish() = {
    ByteString.empty
  }
}

object PlainEncryptionModule extends PlainEncryptionModule // Thread-safe