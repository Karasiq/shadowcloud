package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.SecretBox

import com.karasiq.shadowcloud.crypto._

private[libsodium] object SecretBoxModule {
  val KEY_BYTES = Sodium.CRYPTO_SECRETBOX_KEYBYTES
  val NONCE_BYTES = Sodium.CRYPTO_SECRETBOX_NONCEBYTES

  def apply(method: EncryptionMethod = EncryptionMethod("XSalsa20/Poly1305", 256)): SecretBoxModule = {
    new SecretBoxModule(method)
  }
}

private[libsodium] final class SecretBoxModule(val method: EncryptionMethod) extends SymmetricCipherModule {
  protected val keySize = SecretBoxModule.KEY_BYTES
  protected val nonceSize = SecretBoxModule.NONCE_BYTES
  private[this] var encryptMode = true
  private[this] var secretBox: SecretBox = _
  private[this] var nonce: Array[Byte] = _

  protected def init(encrypt: Boolean, key: Array[Byte], nonce: Array[Byte]): Unit = {
    this.encryptMode = encrypt
    this.secretBox = new SecretBox(key)
    this.nonce = nonce
  }

  protected def process(data: Array[Byte]): Array[Byte] = {
    require(secretBox ne null, "Not initialized")
    if (encryptMode) {
      secretBox.encrypt(nonce, data)
    } else {
      secretBox.decrypt(nonce, data)
    }
  }
}
