package com.karasiq.shadowcloud.crypto.bouncycastle.internal

import java.security.MessageDigest

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}

import scala.language.postfixOps

private[bouncycastle] final class MessageDigestHashingModule(messageDigest: MessageDigest) extends StreamHashingModule {
  val method = {
    HashingMethod.Digest(messageDigest.getAlgorithm)
  }

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
