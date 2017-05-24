package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import scala.language.postfixOps

import org.bouncycastle.crypto.signers.ECDSASigner

import com.karasiq.shadowcloud.crypto.{HashingMethod, SignMethod}

private[bouncycastle] object ECDSASignModule {
  def apply(method: SignMethod = SignMethod("ECDSA", HashingMethod.default)): ECDSASignModule = {
    new ECDSASignModule(method)
  }
}

private[bouncycastle] final class ECDSASignModule(val method: SignMethod) extends BCDSAModule with BCECKeys {
  protected val dsaSigner = new ECDSASigner()
}
