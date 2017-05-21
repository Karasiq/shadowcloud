package com.karasiq.shadowcloud.crypto.bouncycastle.hashing

import java.security.MessageDigest

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.config.utils.ConfigImplicits
import com.karasiq.shadowcloud.crypto.{HashingMethod, StreamHashingModule}
import com.karasiq.shadowcloud.crypto.bouncycastle.internal.BCUtils

private[bouncycastle] object MessageDigestModule extends ConfigImplicits {
  def apply(method: HashingMethod): MessageDigestModule = {
    if (BCDigests.hasTwoSizes(method.algorithm)) {
      forMDWithTwoSizes(method.algorithm, method)
    } else if (BCDigests.hasSize(method.algorithm)) {
      forMDWithSize(method.algorithm, method)
    } else {
      forMessageDigest(method.algorithm, method)
    }
  }

  def apply(alg: String): MessageDigestModule = {
    apply(HashingMethod(alg))
  }

  def forMessageDigest(alg: String, method: HashingMethod): MessageDigestModule = {
    new MessageDigestModule(method, MessageDigest.getInstance(alg, BCUtils.provider))
  }

  def forMDWithSize(alg: String, method: HashingMethod, defaultSize: Int = 256): MessageDigestModule = {
    val config = ConfigProps.toConfig(method.config)
    val digestSize = config.withDefault(defaultSize, _.getInt("digest-size"))
    forMessageDigest(s"$alg-$digestSize", method)
  }

  def forMDWithTwoSizes(alg: String, method: HashingMethod, defaultStateSize: Int = 256): MessageDigestModule = {
    val config = ConfigProps.toConfig(method.config)
    val stateSize = config.withDefault(defaultStateSize, _.getInt("state-size"))
    val digestSize = config.withDefault(stateSize, _.getInt("digest-size"))
    forMessageDigest(s"$alg-$stateSize-$digestSize", method)
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
