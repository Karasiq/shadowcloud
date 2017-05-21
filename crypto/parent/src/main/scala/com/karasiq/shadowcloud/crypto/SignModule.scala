package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import akka.util.ByteString

trait SignModule {
  def createParameters(): SignParameters
  def sign(data: ByteString, parameters: SignParameters): ByteString
  def verify(data: ByteString, signature: ByteString, parameters: SignParameters): Boolean
}

trait StreamSignModule extends SignModule {
  def init(sign: Boolean, parameters: SignParameters): Unit
  def process(data: ByteString): Unit
  def finishVerify(signature: ByteString): Boolean
  def finishSign(): ByteString

  // One pass functions
  def sign(data: ByteString, parameters: SignParameters): ByteString = {
    init(sign = true, parameters)
    process(data)
    finishSign()
  }

  def verify(data: ByteString, signature: ByteString, parameters: SignParameters): Boolean = {
    init(sign = false, parameters)
    process(data)
    finishVerify(signature)
  }
}
