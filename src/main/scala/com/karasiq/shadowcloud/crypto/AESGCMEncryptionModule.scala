package com.karasiq.shadowcloud.crypto

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.KeyGenerator

import akka.util.ByteString
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import scala.language.postfixOps

class AESGCMEncryptionModule(bits: Int = 256) extends EncryptionModule {
  private val secureRandom = SecureRandom.getInstanceStrong
  private val keyGenerator = KeyGenerator.getInstance("AES")
  keyGenerator.init(bits, secureRandom)
  private val aes = new GCMBlockCipher(new AESEngine)

  private def generateIV(): ByteString = {
    val iv = Array.ofDim[Byte](12)
    secureRandom.nextBytes(iv)
    ByteString(iv)
  }

  def createParameters() = {
    EncryptionParameters(EncryptionMethod.AES("GCM", bits), ByteString(keyGenerator.generateKey().getEncoded), generateIV())
  }

  def updateParameters(parameters: EncryptionParameters) = {
    parameters.copy(iv = generateIV())
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters) = {
    aes.reset()
    aes.init(encrypt, new ParametersWithIV(new KeyParameter(parameters.key.toArray), parameters.iv.toArray))
  }

  def process(data: ByteString) = {
    val output = Array.ofDim[Byte](aes.getUpdateOutputSize(data.length))
    val length = aes.processBytes(data.toArray, 0, data.length, output, 0)
    ByteString(ByteBuffer.wrap(output, 0, length))
  }

  def finish() = {
    val output = Array.ofDim[Byte](aes.getOutputSize(0))
    val length = aes.doFinal(output, 0)
    ByteString(ByteBuffer.wrap(output, 0, length))
  }
}
