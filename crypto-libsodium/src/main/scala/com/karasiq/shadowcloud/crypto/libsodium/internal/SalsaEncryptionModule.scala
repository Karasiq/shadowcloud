package com.karasiq.shadowcloud.crypto.libsodium.internal

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto._
import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.{Random, SecretBox}

private[libsodium] final class SalsaEncryptionModule(method: EncryptionMethod) extends StreamEncryptionModule {
  private[this] val random = new Random()
  private[this] var encryptMode = true
  private[this] var lastKey = ByteString.empty
  private[this] var secretBox: SecretBox = _
  private[this] var nonce: Array[Byte] = _

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
      secretBox = new SecretBox(key.toArray)
      lastKey = key
    }
    nonce = parameters1.nonce.toArray
  }

  def process(data: ByteString): ByteString = {
    require(secretBox ne null, "Not initialized")
    val result = if (encryptMode) {
      secretBox.encrypt(nonce, data.toArray)
    } else {
      secretBox.decrypt(nonce, data.toArray)
    }
    ByteString(result)
  }

  def finish(): ByteString = {
    ByteString.empty
  }

  private[this] def generateNonce(): ByteString = {
    ByteString(random.randomBytes(Sodium.XSALSA20_POLY1305_SECRETBOX_NONCEBYTES))
  }

  private[this] def generateKey(): ByteString = {
    ByteString(random.randomBytes(Sodium.XSALSA20_POLY1305_SECRETBOX_KEYBYTES))
  }
}
