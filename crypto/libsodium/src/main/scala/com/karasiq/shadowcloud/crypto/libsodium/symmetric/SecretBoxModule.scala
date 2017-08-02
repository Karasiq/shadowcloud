package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.SecretBox

import com.karasiq.shadowcloud.crypto._

private[libsodium] object SecretBoxModule {
  val KEY_BYTES: Int = Sodium.CRYPTO_SECRETBOX_KEYBYTES
  val NONCE_BYTES: Int = Sodium.CRYPTO_SECRETBOX_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod("XSalsa20/Poly1305", 256)): SecretBoxModule = {
    new SecretBoxModule(method)
  }
}

private[libsodium] final class SecretBoxModule(val method: EncryptionMethod)
  extends SymmetricCipherModule with SymmetricCipherAtomic {

  protected val keySize: Int = SecretBoxModule.KEY_BYTES
  protected val nonceSize: Int = SecretBoxModule.NONCE_BYTES

  protected def encrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    val secretBox = new SecretBox(key)
    secretBox.encrypt(nonce, data)
  }

  protected def decrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    val secretBox = new SecretBox(key)
    secretBox.decrypt(nonce, data)
  }
}
