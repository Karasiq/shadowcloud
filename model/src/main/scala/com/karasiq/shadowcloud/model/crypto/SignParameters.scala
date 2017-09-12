package com.karasiq.shadowcloud.model.crypto

import scala.language.postfixOps

import akka.util.ByteString

@SerialVersionUID(0L)
final case class SignParameters(method: SignMethod, publicKey: ByteString, privateKey: ByteString) extends CryptoParameters {
  @transient
  private[this] val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

  def isEmpty: Boolean = {
    publicKey.isEmpty && privateKey.isEmpty
  }

  def toReadOnly: SignParameters = {
    copy(privateKey = ByteString.empty)
  }

  override def hashCode(): Int = {
    _hashCode
  }

  override def toString: String = {
    s"SignParameters($method, public: ${publicKey.length * 8} bits, private: ${privateKey.length * 8} bits)"
  }
}

object SignParameters {
  val empty = SignParameters(SignMethod.none, ByteString.empty, ByteString.empty)
}