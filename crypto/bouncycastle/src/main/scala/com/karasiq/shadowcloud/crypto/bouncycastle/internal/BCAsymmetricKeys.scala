package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import org.bouncycastle.crypto.AsymmetricCipherKeyPairGenerator

private[bouncycastle] trait BCAsymmetricKeys {
  protected def keyPairGenerator: AsymmetricCipherKeyPairGenerator
}
