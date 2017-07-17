package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import com.karasiq.shadowcloud.crypto.CryptoMethod
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCAsymmetricKeys, ECUtils}

trait BCECKeys extends BCAsymmetricKeys {
  protected def method: CryptoMethod
  protected val keyPairGenerator = ECUtils.createKeyGenerator(method)
}
