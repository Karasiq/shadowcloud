package com.karasiq.shadowcloud.crypto.internal

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{SignMethod, SignParameters, StreamSignModule}

private[crypto] final class NoOpSignModule extends StreamSignModule {
  def method: SignMethod = SignMethod.none
  def init(sign: Boolean, parameters: SignParameters): Unit = ()
  def process(data: ByteString): Unit = ()
  def finishVerify(signature: ByteString): Boolean = true
  def finishSign(): ByteString = ByteString.empty
  def createParameters(): SignParameters = SignParameters.empty
}
