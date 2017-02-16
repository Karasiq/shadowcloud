package com.karasiq.shadowcloud.crypto.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionModule, EncryptionParameters}

import scala.language.postfixOps

private[crypto] final class PlainEncryptionModule extends EncryptionModule {
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
}