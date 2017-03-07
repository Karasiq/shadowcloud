package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import com.karasiq.shadowcloud.crypto.EncryptionMethod
import org.abstractj.kalium.NaCl.Sodium

private[libsodium] object XSalsa20Module {
  val KEY_BYTES = Sodium.CRYPTO_STREAM_KEYBYTES
  val NONCE_BYTES = 24 // Sodium.CRYPTO_STREAM_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod("XSalsa20", KEY_BYTES * 8)): XSalsa20Module = {
    new XSalsa20Module(method)
  }
}

private[libsodium] final class XSalsa20Module(method: EncryptionMethod)
  extends StreamCipherModule(method, XSalsa20Module.KEY_BYTES, XSalsa20Module.NONCE_BYTES) {

  protected def process(inArray: Array[Byte], outArray: Array[Byte]): Unit = {
    sodium.crypto_stream_xor(outArray, inArray, inArray.length, nonce, key)
  }
}
