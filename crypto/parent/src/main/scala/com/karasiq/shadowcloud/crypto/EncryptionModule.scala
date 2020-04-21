package com.karasiq.shadowcloud.crypto



import akka.util.ByteString
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, EncryptionParameters}

trait EncryptionModule extends CryptoModule {
  def method: EncryptionMethod
  def createParameters(): EncryptionParameters
  def updateParameters(parameters: EncryptionParameters): EncryptionParameters
  def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString
  def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString
}

trait EncryptionModuleStreamer extends CryptoModuleStreamer {
  def module: EncryptionModule
  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit
}

trait StreamEncryptionModule extends EncryptionModule with StreamCryptoModule {
  def createStreamer(): EncryptionModuleStreamer
}

trait OnlyStreamEncryptionModule extends StreamEncryptionModule {
  override def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val streamer = this.createStreamer()
    streamer.init(encrypt = true, parameters)
    streamer.process(data) ++ streamer.finish()
  }

  override def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val streamer = this.createStreamer()
    streamer.init(encrypt = false, parameters)
    streamer.process(data) ++ streamer.finish()
  }
}
