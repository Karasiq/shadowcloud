package com.karasiq.shadowcloud.model.crypto

import akka.util.ByteString
import com.karasiq.shadowcloud.index.utils.HasWithoutKeys

@SerialVersionUID(0L)
final case class SignParameters(method: SignMethod, publicKey: ByteString, privateKey: ByteString) extends CryptoParameters with HasWithoutKeys {
  type Repr = SignParameters

  @transient
  private[this] lazy val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

  def isEmpty: Boolean = {
    publicKey.isEmpty && privateKey.isEmpty
  }

  def toReadOnly: SignParameters = {
    copy(privateKey = ByteString.empty)
  }

  def withoutKeys = {
    copy(publicKey = ByteString.empty, privateKey = ByteString.empty)
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
