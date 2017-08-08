package com.karasiq.shadowcloud.crypto.internal

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto._

private[crypto] final class NoOpEncryptionModule extends StreamEncryptionModule {
  def method: EncryptionMethod = EncryptionMethod.none

  def createParameters(): EncryptionParameters = EncryptionParameters.empty
  def updateParameters(parameters: EncryptionParameters): EncryptionParameters =  parameters

  override def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = data
  override def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = data

  def createStreamer(): EncryptionModuleStreamer = NoOpEncryptionStreamer

  private[this] object NoOpEncryptionStreamer extends EncryptionModuleStreamer {
    def module: EncryptionModule = NoOpEncryptionModule.this
    def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = ()
    def process(data: ByteString): ByteString = data
    def finish(): ByteString = ByteString.empty
  }
}