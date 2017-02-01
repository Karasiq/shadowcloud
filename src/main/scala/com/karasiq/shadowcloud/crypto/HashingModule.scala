package com.karasiq.shadowcloud.crypto

import java.security.MessageDigest

import akka.util.ByteString

import scala.language.postfixOps

trait HashingModule {
  def update(data: ByteString): Unit
  def createHash(): ByteString
  def reset(): Unit
}

object HashingModule {
  final class MessageDigestHashingModule(messageDigest: MessageDigest) extends HashingModule {
    messageDigest.reset()

    def update(data: ByteString) = {
      messageDigest.update(data.toArray)
    }

    def createHash() = {
      ByteString(messageDigest.digest())
    }

    def reset() = {
      messageDigest.reset()
    }

    def hash(data: ByteString) = {
      messageDigest.reset()
      ByteString(messageDigest.digest(data.toArray))
    }
  }

  def apply(alg: String): HashingModule = {
    new MessageDigestHashingModule(MessageDigest.getInstance(alg))
  }

  val default = apply("SHA1")
}