package com.karasiq.shadowcloud.crypto

import java.security.MessageDigest

import akka.util.ByteString

import scala.language.postfixOps

trait HashingModule {
  def method: HashingMethod
  def update(data: ByteString): Unit
  def createHash(): ByteString
  def reset(): Unit

  // One pass function
  def createHash(data: ByteString): ByteString = {
    update(data)
    val hash = createHash()
    reset()
    hash
  }
}

object HashingModule {
  final class MessageDigestHashingModule(messageDigest: MessageDigest) extends HashingModule {
    messageDigest.reset()

    val method = HashingMethod.Digest(messageDigest.getAlgorithm)

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

  def apply(method: HashingMethod): HashingModule = method match {
    case HashingMethod.Digest(alg) ⇒
      apply(alg)
  }

  def apply(alg: String): HashingModule = {
    new MessageDigestHashingModule(MessageDigest.getInstance(alg))
  }

  val default = apply(HashingMethod.default)
}