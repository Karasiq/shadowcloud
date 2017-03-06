package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.security.MessageDigest

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}

import scala.language.postfixOps

private[bouncycastle] object MessageDigestHashingModule {
  def apply(method: HashingMethod): MessageDigestHashingModule = {
    new MessageDigestHashingModule(method, MessageDigest.getInstance(method.algorithm, BCUtils.provider))
  }

  def apply(alg: String): MessageDigestHashingModule = {
    apply(HashingMethod(alg))
  }
}

private[bouncycastle] final class MessageDigestHashingModule(val method: HashingMethod, messageDigest: MessageDigest) extends StreamHashingModule {
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
