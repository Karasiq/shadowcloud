package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import akka.util.ByteString

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