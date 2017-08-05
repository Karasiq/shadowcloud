package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.Signer

import com.karasiq.shadowcloud.crypto.{SignParameters, StreamSignModule}

private[bouncycastle] trait BCSignerModule extends StreamSignModule with BCSignKeys {
  protected def signer: Signer

  def init(sign: Boolean, parameters: SignParameters): Unit = {
    requireInitialized()
    signer.init(sign, getSignerKey(parameters, sign))
  }

  def process(data: ByteString): Unit = {
    requireInitialized()
    signer.update(data.toArray, 0, data.length)
  }

  def finishVerify(signature: ByteString): Boolean = {
    requireInitialized()
    signer.verifySignature(signature.toArray)
  }

  def finishSign(): ByteString = {
    requireInitialized()
    ByteString(signer.generateSignature())
  }

  private[this] def requireInitialized(): Unit = {
    require(signer.ne(null), "Not initialized")
  }
}
