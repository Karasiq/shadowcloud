package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.model.crypto.{SignMethod, SignParameters}

trait SignModule extends CryptoModule {
  def method: SignMethod
  def createParameters(): SignParameters
  def sign(data: ByteString, parameters: SignParameters): ByteString
  def verify(data: ByteString, signature: ByteString, parameters: SignParameters): Boolean
}

trait SignModuleStreamer extends CryptoModuleStreamer {
  def module: SignModule
  def init(sign: Boolean, parameters: SignParameters): Unit
  def update(data: ByteString): Unit
  def finishVerify(signature: ByteString): Boolean
  def finishSign(): ByteString

  override def process(data: ByteString): ByteString = {
    update(data)
    ByteString.empty
  }

  override final def finish(): ByteString = finishSign()
}

trait StreamSignModule extends SignModule with StreamCryptoModule {
  def createStreamer(): SignModuleStreamer
}

trait OnlyStreamSignModule extends StreamSignModule {
  def sign(data: ByteString, parameters: SignParameters): ByteString = {
    val streamer = this.createStreamer()
    streamer.init(sign = true, parameters)
    streamer.process(data) ++ streamer.finishSign()
  }

  def verify(data: ByteString, signature: ByteString, parameters: SignParameters): Boolean = {
    val streamer = this.createStreamer()
    streamer.init(sign = false, parameters)
    streamer.process(data)
    streamer.finishVerify(signature)
  }
}
