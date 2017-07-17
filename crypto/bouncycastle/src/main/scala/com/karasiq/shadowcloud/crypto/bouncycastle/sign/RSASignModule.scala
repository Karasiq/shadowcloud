package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import scala.language.postfixOps

import org.bouncycastle.crypto.signers.RSADigestSigner

import com.karasiq.shadowcloud.crypto.{HashingMethod, SignMethod, SignParameters}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.RSAUtils

private[bouncycastle] object RSASignModule {
  def apply(method: SignMethod = SignMethod("RSA", HashingMethod.default, 4096)): RSASignModule = {
    new RSASignModule(method)
  }
}

private[bouncycastle] final class RSASignModule(val method: SignMethod) extends BCSignerModule {
  protected var signer: RSADigestSigner = _
  protected val keyPairGenerator = RSAUtils.createKeyGenerator(method.keySize, RSAUtils.getPublicExponent(method))

  override def init(sign: Boolean, parameters: SignParameters): Unit = {
    this.signer = new RSADigestSigner(BCDigests.createDigest(method.hashingMethod))
    super.init(sign, parameters)
  }
}
