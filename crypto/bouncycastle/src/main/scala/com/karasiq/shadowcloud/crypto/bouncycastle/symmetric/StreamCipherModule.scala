package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.StreamCipher

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCSymmetricKeys, BCUtils}

//noinspection RedundantDefaultArgument
private[bouncycastle] object StreamCipherModule {
  def apply(method: EncryptionMethod): StreamCipherModule = {
    new StreamCipherModule(method,
      BCStreamCiphers.createStreamCipher(method.algorithm),
      BCStreamCiphers.getNonceSize(method.algorithm))
  }

  def Salsa20(): StreamCipherModule = {
    apply(EncryptionMethod("Salsa20", 256))
  }

  def XSalsa20(): StreamCipherModule = {
    apply(EncryptionMethod("XSalsa20", 256))
  }

  def ChaCha20(): StreamCipherModule = {
    apply(EncryptionMethod("ChaCha20", 256))
  }
}

private[bouncycastle] final class StreamCipherModule(val method: EncryptionMethod,
                                                     private[this] val cipher: StreamCipher,
                                                     protected val nonceSize: Int)
  extends StreamEncryptionModule with BCSymmetricKeys {

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    cipher.init(encrypt, BCUtils.toParametersWithIV(parameters))
  }

  def process(data: ByteString): ByteString = {
    val outArray = new Array[Byte](data.length)
    val outLength = cipher.processBytes(data.toArray, 0, data.length, outArray, 0)
    ByteString.fromArray(outArray, 0, outLength)
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
