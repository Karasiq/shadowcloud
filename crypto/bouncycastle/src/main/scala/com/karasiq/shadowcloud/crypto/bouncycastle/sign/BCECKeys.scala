package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCAsymmetricKeys, ECUtils}
import com.karasiq.shadowcloud.model.crypto.CryptoMethod

trait BCECKeys extends BCAsymmetricKeys {
  protected def method: CryptoMethod
  protected val keyPairGenerator = ECUtils.createKeyGenerator(method)
}
