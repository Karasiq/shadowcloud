package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import com.karasiq.shadowcloud.crypto.CryptoMethod
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.{BCAsymmetricKeys, KeyUtils}

trait BCECKeys extends BCAsymmetricKeys {
  protected def method: CryptoMethod

  protected val keyPairGenerator = KeyUtils.ecKeyGenerator(method)
}
