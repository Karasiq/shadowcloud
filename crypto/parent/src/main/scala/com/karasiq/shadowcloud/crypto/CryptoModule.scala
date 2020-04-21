package com.karasiq.shadowcloud.crypto



import akka.util.ByteString
import com.karasiq.shadowcloud.model.crypto.CryptoMethod

trait CryptoModule {
  def method: CryptoMethod
}

trait CryptoModuleStreamer {
  def module: CryptoModule
  def process(data: ByteString): ByteString
  def finish(): ByteString
}

trait StreamCryptoModule extends CryptoModule {
  def createStreamer(): CryptoModuleStreamer
}
