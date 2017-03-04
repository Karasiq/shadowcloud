package com.karasiq.shadowcloud.crypto

import akka.util.ByteString

import scala.language.postfixOps

sealed trait EncryptionParameters extends EncryptionParametersConversions {
  def method: EncryptionMethod
}

sealed trait EncryptionParametersConversions { self: EncryptionParameters ⇒
  def symmetric: SymmetricEncryptionParameters = self match {
    case sym: SymmetricEncryptionParameters ⇒ sym
    case _ ⇒ throw new IllegalArgumentException("Symmetric key parameters required")
  }

  def asymmetric: AsymmetricEncryptionParameters = self match {
    case as: AsymmetricEncryptionParameters ⇒ as
    case _ ⇒ throw new IllegalArgumentException("Asymmetric key parameters required")
  }
}

case object EmptyEncryptionParameters extends EncryptionParameters {
  override val method = EncryptionMethod.none

  override def toString: String = {
    "EncryptionParameters.empty"
  }
}

case class SymmetricEncryptionParameters(method: EncryptionMethod, key: ByteString, iv: ByteString) extends EncryptionParameters {
  override def toString: String = {
    s"SymmetricEncryptionParameters($method, key: ${key.length * 8} bits, iv: ${iv.length * 8} bits)"
  }
}

case class AsymmetricEncryptionParameters(method: EncryptionMethod, publicKey: ByteString, privateKey: ByteString) extends EncryptionParameters {
  override def toString: String = {
    s"AsymmetricEncryptionParameters($method, public: ${publicKey.length * 8} bits, private: ${privateKey.length * 8} bits)"
  }
}

object EncryptionParameters {
  // No encryption
  val empty = EmptyEncryptionParameters
}