package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import org.abstractj.kalium.NaCl.Sodium

import com.karasiq.shadowcloud.model.crypto.EncryptionMethod

private[libsodium] object XSalsa20Module extends SymmetricConstants {
  val KeyBytes   = Sodium.CRYPTO_STREAM_KEYBYTES
  val NonceBytes = Sodium.CRYPTO_STREAM_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod("XSalsa20", KeyBytes * 8)): XSalsa20Module = {
    new XSalsa20Module(method)
  }
}

private[libsodium] final class XSalsa20Module(method: EncryptionMethod)
    extends StreamCipherModule(method, XSalsa20Module.KeyBytes, XSalsa20Module.NonceBytes) {

  protected def process(key: Array[Byte], nonce: Array[Byte], inArray: Array[Byte], outArray: Array[Byte]): Unit = {
    sodium.crypto_stream_xor(outArray, inArray, inArray.length, nonce, key)
  }
}
