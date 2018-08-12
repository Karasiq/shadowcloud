package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import akka.util.ByteString
import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.Aead

import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
import com.karasiq.common.configs.ConfigImplicits
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto.libsodium.symmetric.AEADCipherModule.AEADCipherOptions
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, EncryptionParameters}
import com.karasiq.shadowcloud.utils.ByteStringUnsafe

private[libsodium] object AEADCipherModule extends SymmetricConstants {
  val AESKeyBytes: Int = Sodium.CRYPTO_AEAD_AES256GCM_KEYBYTES
  val AESNonceBytes: Int = Sodium.CRYPTO_AEAD_AES256GCM_NPUBBYTES
  val KeyBytes: Int = Sodium.CRYPTO_AEAD_CHACHA20POLY1305_KEYBYTES
  val NonceBytes: Int = Sodium.CRYPTO_AEAD_CHACHA20POLY1305_NPUBBYTES

  def apply(method: EncryptionMethod): AEADCipherModule = {
    new AEADCipherModule(AEADCipherOptions(method))
  }

  def AES_GCM(): AEADCipherModule = {
    apply(EncryptionMethod("AES/GCM", AESKeyBytes * 8))
  }

  def ChaCha20_Poly1305(): AEADCipherModule = {
    apply(EncryptionMethod("ChaCha20/Poly1305", KeyBytes * 8))
  }

  private case class AEADCipherOptions(method: EncryptionMethod) {
    import ConfigImplicits._
    private[this] val config = ConfigProps.toConfig(method.config)
    val useAes = method.algorithm == "AES/GCM"
    val additionalDataSize = config.withDefault(0, _.getInt("ad-size"))

    val keySize: Int = if (useAes) AESKeyBytes else KeyBytes
    val nonceSize: Int = if (useAes) AESNonceBytes else NonceBytes
  }
}

private[libsodium] final class AEADCipherModule(defaultOptions: AEADCipherOptions) extends SymmetricCipherModule {
  val method: EncryptionMethod = defaultOptions.method
  protected val keySize: Int = defaultOptions.keySize
  protected val nonceSize: Int = defaultOptions.nonceSize + defaultOptions.additionalDataSize

  def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val symmetricParameters = EncryptionParameters.symmetric(parameters)
    val aeadOptions = AEADCipherOptions(symmetricParameters.method)

    val cipher = new Aead(symmetricParameters.key.toArrayUnsafe)
    if (aeadOptions.useAes) cipher.useAesGcm()
    val (nonce, additionalData) = splitNonce(aeadOptions, symmetricParameters.nonce)

    val outArray = cipher.encrypt(ByteStringUnsafe.getArray(nonce), ByteStringUnsafe.getArray(data), ByteStringUnsafe.getArray(additionalData))
    ByteString.fromArrayUnsafe(outArray)
  }

  def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val symmetricParameters = EncryptionParameters.symmetric(parameters)
    val aeadOptions = AEADCipherOptions(symmetricParameters.method)

    val cipher = new Aead(symmetricParameters.key.toArrayUnsafe)
    if (aeadOptions.useAes) cipher.useAesGcm()
    val (nonce, additionalData) = splitNonce(aeadOptions, symmetricParameters.nonce)

    val outArray = cipher.decrypt(ByteStringUnsafe.getArray(nonce), ByteStringUnsafe.getArray(data), ByteStringUnsafe.getArray(additionalData))
    ByteString.fromArrayUnsafe(outArray)
  }

  @inline
  private[this] def splitNonce(options: AEADCipherOptions, value: ByteString): (ByteString, ByteString) = {
    value.splitAt(options.nonceSize)
  }
}
