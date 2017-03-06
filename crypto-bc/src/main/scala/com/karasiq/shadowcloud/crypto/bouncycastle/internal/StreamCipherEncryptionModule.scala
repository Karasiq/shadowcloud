package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.security.SecureRandom

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule, SymmetricEncryptionParameters}
import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.engines.{ChaChaEngine, Salsa20Engine, XSalsa20Engine}
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import scala.language.postfixOps

private[bouncycastle] object StreamCipherEncryptionModule {
  def Salsa20(method: EncryptionMethod = EncryptionMethod("Salsa20", 256)): StreamCipherEncryptionModule = {
    new StreamCipherEncryptionModule(method, new Salsa20Engine(), 8)
  }

  def XSalsa20(method: EncryptionMethod = EncryptionMethod("XSalsa20", 256)): StreamCipherEncryptionModule = {
    new StreamCipherEncryptionModule(method, new XSalsa20Engine(), 24)
  }

  def ChaCha20(method: EncryptionMethod = EncryptionMethod("ChaCha20", 256)): StreamCipherEncryptionModule = {
    new StreamCipherEncryptionModule(method, new ChaChaEngine(), 8)
  }
}

private[bouncycastle] final class StreamCipherEncryptionModule(method: EncryptionMethod, cipher: StreamCipher, nonceSize: Int) extends StreamEncryptionModule {
  private[this] val secureRandom = new SecureRandom()

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val parameters1 = parameters.symmetric
    val bcParameters = new ParametersWithIV(new KeyParameter(parameters1.key.toArray), parameters1.nonce.toArray)
    cipher.init(encrypt, bcParameters)
  }

  def process(data: ByteString): ByteString = {
    val inArray = data.toArray
    val length = inArray.length
    val outArray = new Array[Byte](length)
    cipher.processBytes(inArray, 0, inArray.length, outArray, 0)
    ByteString(outArray)
  }

  def finish(): ByteString = {
    ByteString.empty
  }

  def createParameters(): EncryptionParameters = {
    val nonce = new Array[Byte](nonceSize)
    secureRandom.nextBytes(nonce)
    val key = new Array[Byte](method.keySize / 8)
    secureRandom.nextBytes(key)
    SymmetricEncryptionParameters(method, ByteString(key), ByteString(nonce))
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    val nonce = new Array[Byte](nonceSize)
    secureRandom.nextBytes(nonce)
    parameters.symmetric.copy(nonce = ByteString(nonce))
  }
}
