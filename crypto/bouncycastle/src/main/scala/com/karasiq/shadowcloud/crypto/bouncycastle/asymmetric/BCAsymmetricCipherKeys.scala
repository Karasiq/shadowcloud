package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric

import scala.language.postfixOps

import org.bouncycastle.crypto.params.AsymmetricKeyParameter

import com.karasiq.shadowcloud.crypto.{AsymmetricEncryptionParameters, EncryptionModule, EncryptionParameters}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCAsymmetricKeys, KeyUtils}

private[bouncycastle] object BCAsymmetricCipherKeys {
  def getCipherKey(parameters: EncryptionParameters, encrypt: Boolean): AsymmetricKeyParameter = {
    val ap = EncryptionParameters.asymmetric(parameters)
    if (encrypt) {
      KeyUtils.decodePublicKey(ap.publicKey)
    } else {
      KeyUtils.decodePrivateKey(ap.privateKey)
    }
  }
}

private[bouncycastle] trait BCAsymmetricCipherKeys extends BCAsymmetricKeys { self: EncryptionModule ⇒
  def createParameters(): EncryptionParameters = {
    val keyPair = keyPairGenerator.generateKeyPair()
    AsymmetricEncryptionParameters(method, KeyUtils.encodePublicKey(keyPair.getPublic), KeyUtils.encodePrivateKey(keyPair.getPrivate))
  }

  def updateParameters(parameters: EncryptionParameters): EncryptionParameters = {
    parameters
  }
}
