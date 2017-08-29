package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.SecretBox

import com.karasiq.shadowcloud.crypto.EncryptionMethod

private[libsodium] object SecretBoxModule extends SymmetricConstants {
  val algorithm = "XSalsa20/Poly1305"

  val KeyBytes: Int = Sodium.CRYPTO_SECRETBOX_KEYBYTES
  val NonceBytes: Int = Sodium.CRYPTO_SECRETBOX_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod(algorithm, 256)): SecretBoxModule = {
    new SecretBoxModule(method)
  }
}

private[libsodium] final class SecretBoxModule(val method: EncryptionMethod)
  extends SymmetricCipherModule with SymmetricCipherAtomic {

  protected val keySize: Int = SecretBoxModule.KeyBytes
  protected val nonceSize: Int = SecretBoxModule.NonceBytes

  protected def encrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    val secretBox = new SecretBox(key)
    secretBox.encrypt(nonce, data)
  }

  protected def decrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    val secretBox = new SecretBox(key)
    secretBox.decrypt(nonce, data)
  }
}
