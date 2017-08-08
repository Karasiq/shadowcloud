package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import org.bouncycastle.crypto.params.AsymmetricKeyParameter

import com.karasiq.shadowcloud.crypto.{SignModule, SignParameters}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCAsymmetricKeys, KeyUtils}

private[bouncycastle] object BCSignKeys {
  def getSignerKey(parameters: SignParameters, sign: Boolean): AsymmetricKeyParameter = {
    if (sign) {
      KeyUtils.decodePrivateKey(parameters.privateKey)
    } else {
      KeyUtils.decodePublicKey(parameters.publicKey)
    }
  }
}

private[bouncycastle] trait BCSignKeys extends BCAsymmetricKeys { self: SignModule ⇒
  def createParameters(): SignParameters = {
    val keyPair = keyPairGenerator.generateKeyPair()
    SignParameters(method, KeyUtils.encodePublicKey(keyPair.getPublic), KeyUtils.encodePrivateKey(keyPair.getPrivate))
  }
}