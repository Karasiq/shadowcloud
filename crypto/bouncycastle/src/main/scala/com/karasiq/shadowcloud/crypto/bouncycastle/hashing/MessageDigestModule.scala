package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto._

private[bouncycastle] object MessageDigestModule extends ConfigImplicits {
  def apply(method: HashingMethod): MessageDigestModule = {
    new MessageDigestModule(method)
  }

  def apply(algorithm: String): MessageDigestModule = {
    apply(HashingMethod(algorithm))
  }
}

private[bouncycastle] final class MessageDigestModule(val method: HashingMethod) extends OnlyStreamHashingModule {
  def createStreamer(): HashingModuleStreamer = {
    new MessageDigestStreamer
  }

  protected class MessageDigestStreamer extends HashingModuleStreamer {
    private[this] val messageDigest = BCDigests.createMessageDigest(method)

    def module: HashingModule = {
      MessageDigestModule.this
    }

    def update(data: ByteString): Unit = {
      messageDigest.update(data.toArray)
    }

    def finish(): ByteString = {
      ByteString(messageDigest.digest())
    }

    def reset(): Unit = {
      messageDigest.reset()
    }
  }
}
