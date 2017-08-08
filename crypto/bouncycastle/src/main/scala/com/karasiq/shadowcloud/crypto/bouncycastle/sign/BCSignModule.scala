package com.karasiq.shadowcloud.crypto.bouncycastle.sign

import akka.util.ByteString
import org.bouncycastle.crypto.{DSA, Signer}
import org.bouncycastle.crypto.signers.DSADigestSigner

import com.karasiq.shadowcloud.crypto.{OnlyStreamSignModule, SignModuleStreamer, SignParameters}
import com.karasiq.shadowcloud.crypto.bouncycastle.hashing.BCDigests

private[bouncycastle] trait BCSignModule extends OnlyStreamSignModule with BCSignKeys

private[bouncycastle] trait BCSignerStreamer extends SignModuleStreamer {
  protected def signer: Signer

  def init(sign: Boolean, parameters: SignParameters): Unit = {
    requireInitialized()
    signer.init(sign, BCSignKeys.getSignerKey(parameters, sign))
  }

  def update(data: ByteString): Unit = {
    requireInitialized()
    signer.update(data.toArray, 0, data.length)
  }

  def finishVerify(signature: ByteString): Boolean = {
    requireInitialized()
    signer.verifySignature(signature.toArray)
  }

  def finishSign(): ByteString = {
    requireInitialized()
    ByteString(signer.generateSignature())
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