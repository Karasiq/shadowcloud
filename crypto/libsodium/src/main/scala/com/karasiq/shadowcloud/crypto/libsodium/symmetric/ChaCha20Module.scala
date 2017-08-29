package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import scala.language.postfixOps

import org.abstractj.kalium.NaCl.Sodium

import com.karasiq.shadowcloud.crypto.EncryptionMethod

private[libsodium] object ChaCha20Module extends SymmetricConstants {
  val KeyBytes = Sodium.CRYPTO_STREAM_CHACHA20_KEYBYTES
  val NonceBytes = Sodium.CRYPTO_STREAM_CHACHA20_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod("ChaCha20", KeyBytes * 8)): ChaCha20Module = {
    new ChaCha20Module(method)
  }
}

private[libsodium] final class ChaCha20Module(method: EncryptionMethod)
  extends StreamCipherModule(method, ChaCha20Module.KeyBytes, ChaCha20Module.NonceBytes) {

  protected def process(key: Array[Byte], nonce: Array[Byte], inArray: Array[Byte], outArray: Array[Byte]): Unit = {
    sodium.crypto_stream_chacha20_xor(outArray, inArray, inArray.length, nonce, key)
  }
}

