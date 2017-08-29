package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import org.abstractj.kalium.NaCl.Sodium

import com.karasiq.shadowcloud.model.crypto.EncryptionMethod

private[libsodium] object Salsa20Module extends SymmetricConstants {
  val KeyBytes = Sodium.CRYPTO_STREAM_SALSA20_KEYBYTES
  val NonceBytes = Sodium.CRYPTO_STREAM_SALSA20_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod("Salsa20", KeyBytes * 8)): Salsa20Module = {
    new Salsa20Module(method)
  }
}

private[libsodium] final class Salsa20Module(method: EncryptionMethod)
  extends StreamCipherModule(method, Salsa20Module.KeyBytes, Salsa20Module.NonceBytes) {

  protected def process(key: Array[Byte], nonce: Array[Byte], inArray: Array[Byte], outArray: Array[Byte]): Unit = {
    sodium.crypto_stream_salsa20_xor(outArray, inArray, inArray.length, nonce, key)
  }
}
