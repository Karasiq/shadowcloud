package com.karasiq.shadowcloud.crypto.internal

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.KeyGenerator

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{CryptoUtils, EncryptionMethod, EncryptionModule, EncryptionParameters}
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import scala.language.postfixOps

private[crypto] final class AESGCMEncryptionModule(bits: Int = 256) extends EncryptionModule {
  private[this] val secureRandom = SecureRandom.getInstanceStrong
  private[this] val keyGenerator = KeyGenerator.getInstance("AES", CryptoUtils.provider)
  keyGenerator.init(bits, secureRandom)
  private[this] val aes = new GCMBlockCipher(new AESEngine) // TODO: AESFastEngine, hardware acceleration

  def createParameters(): EncryptionParameters = {
    EncryptionParameters(EncryptionMethod.AES("GCM", bits), ByteString(keyGenerator.generateKey().getEncoded), generateIV())
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    parameters.copy(iv = generateIV())
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val key = parameters.key.toArray
    val iv = parameters.iv.toArray
    val keyParams = new ParametersWithIV(new KeyParameter(key), iv)
    try {
      aes.init(encrypt, keyParams)
    } catch { case _: IllegalArgumentException ⇒
      aes.init(encrypt, new ParametersWithIV(new KeyParameter(key), Array[Byte](0)))
      aes.init(encrypt, keyParams)
    }
  }

  def process(data: ByteString): ByteString = {
    require(aes ne null, "Not initialized")
    val output = Array.ofDim[Byte](aes.getUpdateOutputSize(data.length))
    val length = aes.processBytes(data.toArray, 0, data.length, output, 0)
    ByteString(ByteBuffer.wrap(output, 0, length))
  }

  def finish(): ByteString = {
    require(aes ne null, "Not initialized")
    val output = Array.ofDim[Byte](aes.getOutputSize(0))
    val length = aes.doFinal(output, 0)
    ByteString(ByteBuffer.wrap(output, 0, length))
  }

  private[this] def generateIV(): ByteString = {
    val iv = Array.ofDim[Byte](12)
    secureRandom.nextBytes(iv)
    ByteString(iv)
  }
}
