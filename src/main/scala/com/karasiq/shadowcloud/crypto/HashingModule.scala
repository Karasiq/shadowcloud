package com.karasiq.shadowcloud.crypto

import java.security.MessageDigest

import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.internal.MessageDigestHashingModule

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
  def apply(method: HashingMethod): HashingModule = method match {
    case HashingMethod.Digest(alg) ⇒
      apply(alg)
  }

  def apply(alg: String): HashingModule = {
    new MessageDigestHashingModule(MessageDigest.getInstance(alg))
  }

  val default = apply(HashingMethod.default)
}