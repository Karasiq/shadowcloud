package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import scala.language.postfixOps

import org.bouncycastle.crypto.signers.RSADigestSigner

import com.karasiq.shadowcloud.crypto.{HashingMethod, SignMethod, SignParameters}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.{DigestWrapper, MessageDigestModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.KeyUtils

private[bouncycastle] object RSASignModule {
  def apply(method: SignMethod = SignMethod("RSA", HashingMethod.default, 4096)): RSASignModule = {
    new RSASignModule(method)
  }
}

private[bouncycastle] final class RSASignModule(val method: SignMethod) extends BCSignerModule {
  protected var signer: RSADigestSigner = _
  protected val keyPairGenerator = KeyUtils.rsaKeyGenerator(method.keySize)

  override def init(sign: Boolean, parameters: SignParameters): Unit = {
    val digest = DigestWrapper(MessageDigestModule(method.hashingMethod).messageDigest)
    this.signer = new RSADigestSigner(digest)
    super.init(sign, parameters)
  }
}
