package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import java.security.MessageDigest

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}

private[bouncycastle] object MessageDigestModule extends ConfigImplicits {
  def fromMessageDigest(method: HashingMethod, messageDigest: MessageDigest): MessageDigestModule = {
    new MessageDigestModule(method, messageDigest)
  }

  def apply(method: HashingMethod): MessageDigestModule = {
    fromMessageDigest(method, BCDigests.createMessageDigest(method))
  }

  def apply(algorithm: String): MessageDigestModule = {
    apply(HashingMethod(algorithm))
  }
}

private[bouncycastle] final class MessageDigestModule(val method: HashingMethod, val messageDigest: MessageDigest)
  extends StreamHashingModule {

  def update(data: ByteString): Unit = {
    messageDigest.update(data.toArray)
  }

  def createHash(): ByteString = {
    ByteString(messageDigest.digest())
  }

  def reset(): Unit = {
    messageDigest.reset()
  }
}
