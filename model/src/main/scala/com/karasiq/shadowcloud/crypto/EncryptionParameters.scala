package com.karasiq.shadowcloud.crypto

import akka.util.ByteString
import com.karasiq.shadowcloud.index.utils.HasEmpty

import scala.language.postfixOps

sealed trait EncryptionParameters extends HasEmpty with EncryptionParametersConversions {
  def method: EncryptionMethod
  def isSymmetric: Boolean
}

sealed trait EncryptionParametersConversions { self: EncryptionParameters ⇒
  def symmetric: SymmetricEncryptionParameters = {
    if (self.isSymmetric) {
      self.asInstanceOf[SymmetricEncryptionParameters]
    } else {
      throw new IllegalArgumentException("Symmetric key parameters required")
    }
  }

  def asymmetric: AsymmetricEncryptionParameters = {
    if (!self.isSymmetric) {
      self.asInstanceOf[AsymmetricEncryptionParameters]
    } else {
      throw new IllegalArgumentException("Asymmetric key parameters required")
    }
  }
}

case class SymmetricEncryptionParameters(method: EncryptionMethod, key: ByteString, nonce: ByteString) extends EncryptionParameters {
  val isSymmetric: Boolean = true

  def isEmpty: Boolean = {
    key.isEmpty
  }

  override def toString: String = {
    s"SymmetricEncryptionParameters($method, key: ${key.length * 8} bits, nonce: ${nonce.length * 8} bits)"
  }
}

case class AsymmetricEncryptionParameters(method: EncryptionMethod, publicKey: ByteString, privateKey: ByteString) extends EncryptionParameters {
  val isSymmetric: Boolean = false

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
}