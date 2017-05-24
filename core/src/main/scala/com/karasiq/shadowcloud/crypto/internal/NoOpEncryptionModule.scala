package com.karasiq.shadowcloud.crypto.internal

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule}

private[crypto] final class NoOpEncryptionModule extends StreamEncryptionModule {
  def method: EncryptionMethod = EncryptionMethod.none
  def createParameters(): EncryptionParameters = EncryptionParameters.empty
  def updateParameters(parameters: EncryptionParameters): EncryptionParameters =  parameters
  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = ()
  def process(data: ByteString): ByteString = data
  def finish(): ByteString = ByteString.empty
  override def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = data
  override def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = data
}