package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.Aead

import com.karasiq.shadowcloud.config.{ConfigProps, SerializedProps}
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.EncryptionMethod

private[libsodium] object AEADCipherModule extends ConfigImplicits {
  val AES_KEY_BYTES: Int = Sodium.CRYPTO_AEAD_AES256GCM_KEYBYTES
  val AES_NONCE_BYTES: Int = Sodium.CRYPTO_AEAD_AES256GCM_NPUBBYTES
  val KEY_BYTES: Int = Sodium.CRYPTO_AEAD_CHACHA20POLY1305_KEYBYTES
  val NONCE_BYTES: Int = Sodium.CRYPTO_AEAD_CHACHA20POLY1305_NPUBBYTES

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

private[libsodium] final class AEADCipherModule(val method: EncryptionMethod,
                                                useAes: Boolean = false,
                                                additionalDataSize: Int = 0)
  extends SymmetricCipherModule with SymmetricCipherAtomic {
  
  import AEADCipherModule._

  protected val keySize: Int = if (useAes) AES_KEY_BYTES else KEY_BYTES
  private[this] val pNonceSize: Int = if (useAes) AES_NONCE_BYTES else NONCE_BYTES
  protected val nonceSize: Int = pNonceSize + additionalDataSize

  protected def encrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    val cipher = new Aead(key)
    if (useAes) cipher.useAesGcm()
    val (pNonce, additionalData) = splitNonce(nonce)
    cipher.encrypt(pNonce, data, additionalData)
  }

  protected def decrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    val cipher = new Aead(key)
    if (useAes) cipher.useAesGcm()
    val (pNonce, additionalData) = splitNonce(nonce)
    cipher.decrypt(pNonce, data, additionalData)
  }

  @inline
  private[this] def splitNonce(value: Array[Byte]): (Array[Byte], Array[Byte]) = {
    value.splitAt(pNonceSize)
  }
}
