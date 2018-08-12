package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import akka.util.ByteString
import org.bouncycastle.crypto.{DSA, Signer}
import org.bouncycastle.crypto.signers.DSADigestSigner

import com.karasiq.shadowcloud.utils.ByteStringUnsafe.implicits._
import com.karasiq.shadowcloud.crypto.{OnlyStreamSignModule, SignModuleStreamer}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests
import com.karasiq.shadowcloud.model.crypto.SignParameters
import com.karasiq.shadowcloud.utils.ByteStringUnsafe

private[bouncycastle] trait BCSignModule extends OnlyStreamSignModule with BCSignKeys

private[bouncycastle] trait BCSignerStreamer extends SignModuleStreamer {
  protected def signer: Signer

  def init(sign: Boolean, parameters: SignParameters): Unit = {
    requireInitialized()
    signer.init(sign, BCSignKeys.getSignerKey(parameters, sign))
  }

  def update(data: ByteString): Unit = {
    requireInitialized()
    signer.update(data.toArrayUnsafe, 0, data.length)
  }

  def finishVerify(signature: ByteString): Boolean = {
    requireInitialized()
    signer.verifySignature(signature.toArrayUnsafe)
  }

  def finishSign(): ByteString = {
    requireInitialized()
    ByteString.fromArrayUnsafe(signer.generateSignature())
  }

  private[this] def requireInitialized(): Unit = {
    require(signer.ne(null), "Not initialized")
  }
}

private[bouncycastle] trait BCDSAStreamer extends BCSignerStreamer {
  protected def dsaSigner: DSA
  protected var signer: Signer = _

  override def init(sign: Boolean, parameters: SignParameters): Unit = {
    val dsaSigner = this.dsaSigner
    require(dsaSigner.ne(null), "No DSA signer")
    this.signer = new DSADigestSigner(dsaSigner, BCDigests.createDigest(parameters.method.hashingMethod))
    super.init(sign, parameters)
  }
}