package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import scala.language.postfixOps

import akka.util.ByteString
import org.abstractj.kalium.NaCl
import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.{Random ⇒ LSRandom}

import com.karasiq.shadowcloud.crypto.{EncryptionModule, EncryptionParameters, StreamEncryptionModule, SymmetricEncryptionParameters}

private[libsodium] trait SymmetricCipherModule extends EncryptionModule {
  protected final val sodium: Sodium = NaCl.sodium()
  protected final val random: LSRandom = new LSRandom

  protected val keySize: Int
  protected val nonceSize: Int

  def createParameters(): EncryptionParameters = {
    val key = random.randomBytes(keySize)
    val nonce = random.randomBytes(nonceSize)
    SymmetricEncryptionParameters(method, ByteString(key), ByteString(nonce))
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    val nonce = ByteString(random.randomBytes(nonceSize))
    EncryptionParameters.symmetric(parameters).copy(nonce = nonce)
  }
}

private[libsodium] trait SymmetricCipherAtomic extends SymmetricCipherModule {
  protected def encrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte]
  protected def decrypt(data: Array[Byte], key: Array[Byte], nonce: Array[Byte]): Array[Byte]

  override def encrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val sp = EncryptionParameters.symmetric(parameters)
    require(sp.key.length == keySize && sp.nonce.length == nonceSize)
    val result = encrypt(data.toArray, sp.key.toArray, sp.nonce.toArray)
    ByteString(result)
  }

  override def decrypt(data: ByteString, parameters: EncryptionParameters): ByteString = {
    val sp = EncryptionParameters.symmetric(parameters)
    require(sp.key.length == keySize && sp.nonce.length == nonceSize)
    val result = decrypt(data.toArray, sp.key.toArray, sp.nonce.toArray)
    ByteString(result)
  }
}

private[libsodium] trait SymmetricCipherStreaming extends SymmetricCipherModule with StreamEncryptionModule {
  protected def init(encrypt: Boolean, key: Array[Byte], nonce: Array[Byte]): Unit
  protected def process(data: Array[Byte]): Array[Byte]

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val sp = EncryptionParameters.symmetric(parameters)
    require(sp.key.length == keySize && sp.nonce.length == nonceSize)
    init(encrypt, sp.key.toArray, sp.nonce.toArray)
  }

  def process(data: ByteString): ByteString = {
    val result = process(data.toArray)
    ByteString(result)
  }

  def finish(): ByteString = {
    ByteString.empty
  }
}
