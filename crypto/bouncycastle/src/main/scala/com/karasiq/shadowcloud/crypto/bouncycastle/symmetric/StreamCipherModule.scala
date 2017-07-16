package com.karasiq.shadowcloud.crypto.bouncycastle.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.bouncycastle.crypto.StreamCipher
import org.bouncycastle.crypto.params.{KeyParameter, ParametersWithIV}

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCSymmetricKeys

//noinspection RedundantDefaultArgument
private[bouncycastle] object StreamCipherModule {
  def apply(method: EncryptionMethod): StreamCipherModule = {
    val cipher = BCStreamCiphers.createStreamCipher(method.algorithm)
    val nonceSize = BCStreamCiphers.getNonceSize(method.algorithm)
    new StreamCipherModule(method, cipher, nonceSize)
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
    val sp = EncryptionParameters.symmetric(parameters)
    val bcParameters = new ParametersWithIV(new KeyParameter(sp.key.toArray), sp.nonce.toArray)
    cipher.init(encrypt, bcParameters)
  }

  def process(data: ByteString): ByteString = {
    val inArray = data.toArray
    val length = inArray.length
    val outArray = new Array[Byte](length)
    val outLength = cipher.processBytes(inArray, 0, length, outArray, 0)
    ByteString.fromArray(outArray, 0, outLength)
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
