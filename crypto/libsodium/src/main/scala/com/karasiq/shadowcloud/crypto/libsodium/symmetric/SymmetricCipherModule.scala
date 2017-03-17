package com.karasiq.shadowcloud.crypto.libsodium.symmetric

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionParameters, StreamEncryptionModule, SymmetricEncryptionParameters}
import org.abstractj.kalium.NaCl
import org.abstractj.kalium.NaCl.Sodium
import org.abstractj.kalium.crypto.{Random => LSRandom}

import scala.language.postfixOps

private[libsodium] trait SymmetricCipherModule extends StreamEncryptionModule {
  protected final val sodium: Sodium = NaCl.sodium()
  protected final val random: LSRandom = new LSRandom

  protected val method: EncryptionMethod
  protected val keySize: Int
  protected val nonceSize: Int
  protected def init(encrypt: Boolean, key: Array[Byte], nonce: Array[Byte]): Unit
  protected def process(data: Array[Byte]): Array[Byte]

  def init(encrypt: Boolean, parameters: EncryptionParameters): Unit = {
    val sp = parameters.symmetric
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

  def createParameters(): EncryptionParameters = {
    val key = random.randomBytes(keySize)
    val nonce = random.randomBytes(nonceSize)
    SymmetricEncryptionParameters(method, ByteString(key), ByteString(nonce))
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    val nonce = random.randomBytes(nonceSize)
    parameters.symmetric.copy(nonce = ByteString(nonce))
  }
}
