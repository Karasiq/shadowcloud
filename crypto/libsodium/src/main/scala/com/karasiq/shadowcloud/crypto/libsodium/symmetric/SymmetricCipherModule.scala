package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.abstractj.kalium.NaCl

import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.libsodium.internal.LSUtils
import com.karasiq.shadowcloud.model.crypto.{EncryptionParameters, SymmetricEncryptionParameters}

private[libsodium] object SymmetricCipherModule {
  def requireValidParameters(module: SymmetricCipherModule, parameters: SymmetricEncryptionParameters): Unit = {
    require(parameters.key.length == module.keySize && parameters.nonce.length == module.nonceSize, "Key/nonce size not match")
  }
}

private[libsodium] trait SymmetricCipherModule extends EncryptionModule {
  protected final val sodium = NaCl.sodium()
  protected final val secureRandom = LSUtils.createSecureRandom()

  protected val keySize: Int
  protected val nonceSize: Int

  def createParameters(): EncryptionParameters = {
    val key = secureRandom.randomBytes(keySize)
    val nonce = secureRandom.randomBytes(nonceSize)
    SymmetricEncryptionParameters(method, ByteString(key), ByteString(nonce))
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    val nonce = ByteString.fromArrayUnsafe(secureRandom.randomBytes(nonceSize))
    EncryptionParameters.symmetric(parameters).copy(nonce = nonce)
  }
}

private[libsodium] trait SymmetricCipherAtomic extends SymmetricCipherModule {
  protected def encrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte]
  protected def decrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte]

  override def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val symmetricParameters = EncryptionParameters.symmetric(parameters)
    SymmetricCipherModule.requireValidParameters(this, symmetricParameters)
    val outArray = encrypt(data.toArray, symmetricParameters.key.toArray, symmetricParameters.nonce.toArray)
    ByteString.fromArrayUnsafe(outArray)
  }

  override def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val symmetricParameters = EncryptionParameters.symmetric(parameters)
    SymmetricCipherModule.requireValidParameters(this, symmetricParameters)
    val outArray = decrypt(data.toArray, symmetricParameters.key.toArray, symmetricParameters.nonce.toArray)
    ByteString.fromArrayUnsafe(outArray)
  }
}

private[libsodium] trait SymmetricCipherStreaming extends EncryptionModuleStreamer {
  protected def init(encrypt: Boolean, key: Array[Byte], nonce: Array[Byte]): Unit
  protected def process(data: Array[Byte]): Array[Byte]

  def module: SymmetricCipherModule

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val symmetricParameters = EncryptionParameters.symmetric(parameters)
    SymmetricCipherModule.requireValidParameters(module, symmetricParameters)
    init(encrypt, symmetricParameters.key.toArray, symmetricParameters.nonce.toArray)
  }

  def process(data: ByteString): ByteString = {
    val outArray = process(data.toArray)
    ByteString.fromArrayUnsafe(outArray)
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
