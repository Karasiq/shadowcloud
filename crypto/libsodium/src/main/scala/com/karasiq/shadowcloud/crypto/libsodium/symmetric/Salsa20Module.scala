package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import com.karasiq.shadowcloud.crypto.EncryptionMethod
import org.abstractj.kalium.NaCl.Sodium

private[libsodium] object Salsa20Module {
  val KEY_BYTES = Sodium.CRYPTO_STREAM_SALSA20_KEYBYTES
  val NONCE_BYTES = Sodium.CRYPTO_STREAM_SALSA20_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod("Salsa20", KEY_BYTES * 8)): Salsa20Module = {
    new Salsa20Module(method)
  }
}

private[libsodium] final class Salsa20Module(method: EncryptionMethod)
  extends StreamCipherModule(method, Salsa20Module.KEY_BYTES, Salsa20Module.NONCE_BYTES) {

  protected def process(inArray: Array[Byte], outArray: Array[Byte]): Unit = {
    sodium.crypto_stream_salsa20_xor(outArray, inArray, inArray.length, nonce, key)
  }
}
