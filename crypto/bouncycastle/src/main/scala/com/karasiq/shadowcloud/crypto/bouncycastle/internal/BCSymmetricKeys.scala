package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.security.SecureRandom

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, EncryptionParameters, SymmetricEncryptionParameters}

private[bouncycastle] trait BCSymmetricKeys { self: EncryptionModule ⇒
  private[this] val secureRandom = new SecureRandom()

  protected def method: EncryptionMethod
  protected def keySize: Int = method.keySize / 8
  protected def nonceSize: Int

  def createParameters(): SymmetricEncryptionParameters = {
    SymmetricEncryptionParameters(method, generateBytes(keySize), generateBytes(nonceSize))
  }

  def updateParameters(parameters: EncryptionParameters): SymmetricEncryptionParameters = {
    EncryptionParameters.symmetric(parameters).copy(nonce = generateBytes(nonceSize))
  }

  protected def generateBytes(size: Int): ByteString = {
    val result = new Array[Byte](size)
    secureRandom.nextBytes(result)
    ByteString(result)
  }
}
