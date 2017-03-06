package com.karasiq.shadowcloud.crypto.libsodium.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.crypto._
import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.{Aead, Random}

import scala.util.Try

private[libsodium] final class AEADEncryptionModule(useAes: Boolean, method: EncryptionMethod) extends StreamEncryptionModule {
  private[this] val KEY_BYTES = if (useAes) Sodium.CRYPTO_AEAD_AES256GCM_KEYBYTES else Sodium.CRYPTO_AEAD_CHACHA20POLY1305_KEYBYTES
  private[this] val NONCE_BYTES = if (useAes) Sodium.CRYPTO_AEAD_AES256GCM_NPUBBYTES else Sodium.CRYPTO_AEAD_CHACHA20POLY1305_NPUBBYTES

  private[this] val additionalDataSize: Int = Try(ConfigProps.toConfig(method.config))
    .map(_.getInt("ad-size"))
    .getOrElse(0)
  private[this] val random = new Random()
  private[this] var encryptMode = true
  private[this] var lastKey = ByteString.empty
  private[this] var cipher: Aead = _
  private[this] var nonce, additionalData: Array[Byte] = _

  def createParameters(): EncryptionParameters = {
    SymmetricEncryptionParameters(method, generateKey(), generateNonce())
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    parameters.symmetric.copy(nonce = generateNonce())
  }

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    encryptMode = encrypt
    val parameters1 = parameters.symmetric
    val key = parameters1.key
    if (lastKey != key) {
      cipher = new Aead(key.toArray)
      if (useAes) cipher.useAesGcm()
      lastKey = key
    }
    setNonce(parameters1.nonce)
  }

  def process(data: ByteString): ByteString = {
    require(cipher ne null, "Not initialized")
    val result = if (encryptMode) {
      cipher.encrypt(nonce, data.toArray, additionalData)
    } else {
      cipher.decrypt(nonce, data.toArray, additionalData)
    }
    ByteString(result)
  }

  def finish(): ByteString = {
    ByteString.empty
  }

  private[this] def setNonce(value: ByteString): Unit = {
    val (part1, part2) = value.toArray.splitAt(NONCE_BYTES)
    this.nonce = part1
    this.additionalData = part2
  }

  private[this] def generateNonce(): ByteString = {
    ByteString(random.randomBytes(NONCE_BYTES + additionalDataSize))
  }

  private[this] def generateKey(): ByteString = {
    ByteString(random.randomBytes(KEY_BYTES))
  }
}
