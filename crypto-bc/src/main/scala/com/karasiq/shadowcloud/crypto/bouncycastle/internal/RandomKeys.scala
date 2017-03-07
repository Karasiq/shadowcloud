package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.security.SecureRandom

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, EncryptionParameters, SymmetricEncryptionParameters}

import scala.language.postfixOps

private[bouncycastle] trait RandomKeys { self: EncryptionModule ⇒
  private[this] val secureRandom = new SecureRandom()

  protected def method: EncryptionMethod
  protected def keySize: Int = method.keySize / 8
  protected def nonceSize: Int

  def createParameters(): SymmetricEncryptionParameters = {
    SymmetricEncryptionParameters(method, generateBytes(keySize), generateBytes(nonceSize))
  }

  def updateParameters(parameters: EncryptionParameters): SymmetricEncryptionParameters = {
    parameters.symmetric.copy(nonce = generateBytes(nonceSize))
  }

  protected def generateBytes(size: Int): ByteString = {
    val result = new Array[Byte](size)
    secureRandom.nextBytes(result)
    ByteString(result)
  }
}
