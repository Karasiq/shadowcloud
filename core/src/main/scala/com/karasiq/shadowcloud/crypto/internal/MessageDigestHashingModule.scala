package com.karasiq.shadowcloud.crypto.internal

import java.security.MessageDigest

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.{HashingMethod, HashingModule}

import scala.language.postfixOps

private[crypto] final class MessageDigestHashingModule(messageDigest: MessageDigest) extends HashingModule {
  messageDigest.reset()

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

  def hash(data: ByteString): ByteString = {
    messageDigest.reset()
    ByteString(messageDigest.digest(data.toArray))
  }
}
