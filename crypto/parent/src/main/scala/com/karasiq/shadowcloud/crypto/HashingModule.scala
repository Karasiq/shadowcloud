package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import akka.util.ByteString

trait HashingModule extends CryptoModule {
  def method: HashingMethod
  def createHash(data: ByteString): ByteString
}

trait HashingModuleStreamer extends CryptoModuleStreamer {
  def module: HashingModule

  def update(data: ByteString): Unit
  def reset(): Unit

  override def process(data: ByteString): ByteString = {
    update(data)
    ByteString.empty
  }
}

trait StreamHashingModule extends HashingModule with StreamCryptoModule {
  def createStreamer(): HashingModuleStreamer
}

trait OnlyStreamHashingModule extends StreamHashingModule {
  override def createHash(data: ByteString): ByteString = {
    val streamer = this.createStreamer()
    streamer.reset()
    streamer.process(data)
    streamer.finish()
  }
}