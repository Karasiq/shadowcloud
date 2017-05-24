package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import scala.language.postfixOps

import org.bouncycastle.crypto.{DSA, Signer}
import org.bouncycastle.crypto.signers.DSADigestSigner

import com.karasiq.shadowcloud.crypto.SignParameters
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.{DigestWrapper, MessageDigestModule}

private[bouncycastle] trait BCDSAModule extends BCSignerModule {
  protected var signer: Signer = _
  protected def dsaSigner: DSA

  override def init(sign: Boolean, parameters: SignParameters): Unit = {
    require(dsaSigner.ne(null), "No DSA signer")
    val digest = DigestWrapper(MessageDigestModule(parameters.method.hashingMethod).messageDigest)
    this.signer = new DSADigestSigner(dsaSigner, digest)
    super.init(sign, parameters)
  }
}
