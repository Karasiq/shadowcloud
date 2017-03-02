package com.karasiq.shadowcloud.crypto

import java.security.{MessageDigest, Provider}

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
  val default = apply(HashingMethod.default)

  def apply(method: HashingMethod): HashingModule = method match {
    case HashingMethod.Digest(alg) ⇒
      digest(alg)
  }

  def apply(alg: String): HashingModule = {
    digest(alg)
  }

  def digest(alg: String, provider: Option[Provider] = None): HashingModule = {
    val md = if (provider.isEmpty) MessageDigest.getInstance(alg) else MessageDigest.getInstance(alg, provider.get)
    fromMessageDigest(md)
  }

  def fromMessageDigest(md: MessageDigest): HashingModule = {
    new MessageDigestHashingModule(md)
  }
}