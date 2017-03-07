package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import java.security.MessageDigest

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCUtils
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}

import scala.language.postfixOps

private[bouncycastle] object JavaMessageDigestModule {
  def apply(method: HashingMethod): JavaMessageDigestModule = {
    new JavaMessageDigestModule(method, MessageDigest.getInstance(method.algorithm, BCUtils.provider))
  }

  def apply(alg: String): JavaMessageDigestModule = {
    apply(HashingMethod(alg))
  }
}

private[bouncycastle] final class JavaMessageDigestModule(val method: HashingMethod, messageDigest: MessageDigest) extends StreamHashingModule {
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
