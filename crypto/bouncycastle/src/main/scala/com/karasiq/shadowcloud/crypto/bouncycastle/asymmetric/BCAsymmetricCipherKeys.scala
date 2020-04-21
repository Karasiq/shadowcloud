package com.karasiq.shadowcloud.crypto.bouncycastle.asymmetric



import com.karasiq.shadowcloud.crypto.EncryptionModule
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCAsymmetricKeys, KeyUtils}
import com.karasiq.shadowcloud.model.crypto.{AsymmetricEncryptionParameters, EncryptionParameters}
import org.bouncycastle.crypto.params.AsymmetricKeyParameter

private[bouncycastle] object BCAsymmetricCipherKeys {
  def getCipherKey(parameters: EncryptionParameters, encrypt: Boolean): AsymmetricKeyParameter = {
    val asymmetricParameters = EncryptionParameters.asymmetric(parameters)
    if (encrypt) {
      KeyUtils.decodePublicKey(asymmetricParameters.publicKey)
    } else {
      KeyUtils.decodePrivateKey(asymmetricParameters.privateKey)
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
