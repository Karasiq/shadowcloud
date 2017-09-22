package com.karasiq.shadowcloud.model.crypto

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.index.utils.HasWithoutKeys

sealed trait EncryptionParameters extends CryptoParameters with HasWithoutKeys {
  type Repr <: EncryptionParameters
  def method: EncryptionMethod
}

@SerialVersionUID(0L)
final case class SymmetricEncryptionParameters(method: EncryptionMethod,
                                               key: ByteString,
                                               nonce: ByteString) extends EncryptionParameters {

  type Repr = SymmetricEncryptionParameters

  @transient
  private[this] lazy val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

  def isEmpty: Boolean = {
    key.isEmpty
  }
  
  def withoutKeys = {
    copy(key = ByteString.empty, nonce = ByteString.empty)
  }

  override def hashCode(): Int = {
    _hashCode
  }

  override def toString: String = {
    s"SymmetricEncryptionParameters($method, key: ${key.length * 8} bits, nonce: ${nonce.length * 8} bits)"
  }
}

@SerialVersionUID(0L)
final case class AsymmetricEncryptionParameters(method: EncryptionMethod,
                                                publicKey: ByteString,
                                                privateKey: ByteString)
  extends EncryptionParameters with HasWithoutKeys {

  type Repr = AsymmetricEncryptionParameters

  @transient
  private[this] lazy val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

  def toWriteOnly: AsymmetricEncryptionParameters = {
    copy(privateKey = ByteString.empty)
  }

  def isEmpty: Boolean = {
    publicKey.isEmpty && privateKey.isEmpty
  }

  def withoutKeys = {
    copy(publicKey = ByteString.empty, privateKey = ByteString.empty)
  }

  override def hashCode(): Int = {
    _hashCode
  }

  override def toString: String = {
    s"AsymmetricEncryptionParameters($method, public: ${publicKey.length * 8} bits, private: ${privateKey.length * 8} bits)"
  }
}

object EncryptionParameters {
  // No encryption
  val empty = SymmetricEncryptionParameters(EncryptionMethod.none, ByteString.empty, ByteString.empty)

  // Conversions
  def symmetric(p: EncryptionParameters): SymmetricEncryptionParameters = p match {
    case sp: SymmetricEncryptionParameters ⇒
      sp

    case _ ⇒
      throw new IllegalStateException("Symmetric key parameters required")
  }

  def asymmetric(p: EncryptionParameters): AsymmetricEncryptionParameters = p match {
    case ap: AsymmetricEncryptionParameters ⇒
      ap

    case _ ⇒
      throw new IllegalStateException("Asymmetric key parameters required")
  }
}