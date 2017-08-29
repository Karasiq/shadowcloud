package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import akka.util.ByteString

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{HashingModule, HashingModuleStreamer, OnlyStreamHashingModule}
import com.karasiq.shadowcloud.model.crypto.HashingMethod

private[bouncycastle] object BCDigestModule extends ConfigImplicits {
  def apply(method: HashingMethod): BCDigestModule = {
    new BCDigestModule(method)
  }

  def apply(algorithm: String): BCDigestModule = {
    apply(HashingMethod(algorithm))
  }
}

private[bouncycastle] final class BCDigestModule(val method: HashingMethod) extends OnlyStreamHashingModule {
  def createStreamer(): HashingModuleStreamer = {
    new BCDigestStreamer
  }

  protected class BCDigestStreamer extends HashingModuleStreamer {
    private[this] val digest = BCDigests.createDigest(method)

    def module: HashingModule = {
      BCDigestModule.this
    }

    def update(data: ByteString): Unit = {
      digest.update(data.toArray, 0, data.length)
    }

    def finish(): ByteString = {
      val outArray = new Array[Byte](digest.getDigestSize)
      digest.doFinal(outArray, 0)
      ByteString(outArray)
    }

    def reset(): Unit = {
      digest.reset()
    }
  }
}