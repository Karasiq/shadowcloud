package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.Aead

import com.karasiq.shadowcloud.config.{ConfigProps, SerializedProps}
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._

private[libsodium] object AEADCipherModule extends ConfigImplicits {
  val AES_KEY_BYTES = Sodium.CRYPTO_AEAD_AES256GCM_KEYBYTES
  val AES_NONCE_BYTES = Sodium.CRYPTO_AEAD_AES256GCM_NPUBBYTES
  val KEY_BYTES = Sodium.CRYPTO_AEAD_CHACHA20POLY1305_KEYBYTES
  val NONCE_BYTES = Sodium.CRYPTO_AEAD_CHACHA20POLY1305_NPUBBYTES

  def AES_GCM(method: EncryptionMethod = EncryptionMethod("AES/GCM", AES_KEY_BYTES * 8)): AEADCipherModule = {
    new AEADCipherModule(method, true, getADSize(method.config))
  }

  def ChaCha20_Poly1305(method: EncryptionMethod = EncryptionMethod("ChaCha20/Poly1305", KEY_BYTES * 8)): AEADCipherModule = {
    new AEADCipherModule(method, false, getADSize(method.config))
  }

  private[this] def getADSize(props: SerializedProps): Int = {
    val config = ConfigProps.toConfig(props)
    config.withDefault(0, _.getInt("ad-size"))
  }
}

private[libsodium] final class AEADCipherModule(val method: EncryptionMethod, useAes: Boolean = false,
                                                additionalDataSize: Int = 0) extends SymmetricCipherModule {
  import AEADCipherModule._
  protected val keySize = if (useAes) AES_KEY_BYTES else KEY_BYTES
  private[this] val pNonceSize = if (useAes) AES_NONCE_BYTES else NONCE_BYTES
  protected val nonceSize = pNonceSize + additionalDataSize

  private[this] var encryptMode = true
  private[this] var cipher: Aead = _
  private[this] var nonce, additionalData: Array[Byte] = _

  protected def init(encrypt: Boolean, key: Array[Byte], nonce: Array[Byte]): Unit = {
    this.encryptMode = encrypt
    cipher = new Aead(key)
    if (useAes) cipher.useAesGcm()
    setNonce(nonce)
  }

  protected def process(data: Array[Byte]): Array[Byte] = {
    require(cipher ne null, "Not initialized")
    if (encryptMode) {
      cipher.encrypt(nonce, data, additionalData)
    } else {
      cipher.decrypt(nonce, data, additionalData)
    }
  }

  @inline
  private[this] def setNonce(value: Array[Byte]): Unit = {
    val (part1, part2) = value.splitAt(pNonceSize)
    this.nonce = part1
    this.additionalData = part2
  }
}
