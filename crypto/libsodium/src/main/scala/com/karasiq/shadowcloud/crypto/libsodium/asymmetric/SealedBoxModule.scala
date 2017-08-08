package com.karasiq.shadowcloud.crypto.libsodium.asymmetric

import akka.util.ByteString
import org.abstractj.kalium.crypto.SealedBox
import org.abstractj.kalium.keys.KeyPair

import com.karasiq.shadowcloud.crypto.{AsymmetricEncryptionParameters, EncryptionMethod, EncryptionModule, EncryptionParameters}

private[libsodium] object SealedBoxModule {
  val algorithm = "X25519+XSalsa20/Poly1305"

  def apply(method: EncryptionMethod = EncryptionMethod(algorithm)): SealedBoxModule = {
    new SealedBoxModule(method)
  }                                                             
}

/**
  * @see [[https://download.libsodium.org/doc/public-key_cryptography/sealed_boxes.html]]
  */
private[libsodium] final class SealedBoxModule(val method: EncryptionMethod) extends EncryptionModule {
  def createParameters(): EncryptionParameters = {
    val keyPair = new KeyPair()
    AsymmetricEncryptionParameters(method, publicKey = ByteString(keyPair.getPublicKey.toBytes),
      privateKey = ByteString(keyPair.getPrivateKey.toBytes))
  }

  def updateParameters(parameters: EncryptionParameters) = {
    parameters
  }

  def encrypt(data: ByteString, parameters: EncryptionParameters) = {
    val ap = EncryptionParameters.asymmetric(parameters)
    val sealedBox = new SealedBox(ap.publicKey.toArray)
    val outArray = sealedBox.encrypt(data.toArray)
    ByteString(outArray)
  }

  def decrypt(data: ByteString, parameters: EncryptionParameters) = {
    val ap = EncryptionParameters.asymmetric(parameters)
    val sealedBox = new SealedBox(ap.publicKey.toArray, ap.privateKey.toArray)
    val outArray = sealedBox.decrypt(data.toArray)
    ByteString(outArray)
  }
}
