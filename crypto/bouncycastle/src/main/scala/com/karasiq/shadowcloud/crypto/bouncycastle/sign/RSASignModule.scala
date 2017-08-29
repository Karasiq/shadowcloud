package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import scala.language.postfixOps

import org.bouncycastle.crypto.Signer
import org.bouncycastle.crypto.signers.RSADigestSigner

import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.RSAUtils
import com.karasiq.shadowcloud.model.crypto.{HashingMethod, SignMethod, SignParameters}

private[bouncycastle] object RSASignModule {
  // Digest should be in org.bouncycastle.crypto.signers.RSADigestSigner.oidMap
  def apply(method: SignMethod = SignMethod("RSA", HashingMethod("SHA3"), 4096)): RSASignModule = {
    new RSASignModule(method)
  }
}

private[bouncycastle] final class RSASignModule(val method: SignMethod) extends BCSignModule {
  protected val keyPairGenerator = RSAUtils.createKeyGenerator(method)

  def createStreamer(): SignModuleStreamer = {
    new RSASignStreamer
  }

  protected class RSASignStreamer extends BCSignerStreamer {
    protected var signer: Signer = _

    override def init(sign: Boolean, parameters: SignParameters): Unit = {
      this.signer = new RSADigestSigner(BCDigests.createDigest(parameters.method.hashingMethod))
      super.init(sign, parameters)
    }

    def module: SignModule = {
      RSASignModule.this
    }
  }
}
