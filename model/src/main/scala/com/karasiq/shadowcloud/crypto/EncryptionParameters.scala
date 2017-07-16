package com.karasiq.shadowcloud.crypto

import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.index.utils.HasEmpty

sealed trait EncryptionParameters extends Serializable with HasEmpty {
  def method: EncryptionMethod
}

case class SymmetricEncryptionParameters(method: EncryptionMethod, key: ByteString, nonce: ByteString) extends EncryptionParameters {
  def isEmpty: Boolean = {
    key.isEmpty
  }

  override def toString: String = {
    s"SymmetricEncryptionParameters($method, key: ${key.length * 8} bits, nonce: ${nonce.length * 8} bits)"
  }
}

case class AsymmetricEncryptionParameters(method: EncryptionMethod, publicKey: ByteString, privateKey: ByteString) extends EncryptionParameters {
  def isEmpty: Boolean = {
    publicKey.isEmpty && privateKey.isEmpty
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
      throw new IllegalArgumentException("Symmetric key parameters required")
  }

  def asymmetric(p: EncryptionParameters): AsymmetricEncryptionParameters = p match {
    case ap: AsymmetricEncryptionParameters ⇒
      ap

    case _ ⇒
      throw new IllegalArgumentException("Asymmetric key parameters required")
  }
}