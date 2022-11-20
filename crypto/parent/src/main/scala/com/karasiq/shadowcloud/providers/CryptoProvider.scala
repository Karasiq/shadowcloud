package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.model.crypto.{EncryptionMethod, HashingMethod, SignMethod}

abstract class CryptoProvider extends ModuleProvider {
  type HashingPF    = PartialFunction[HashingMethod, HashingModule]
  type EncryptionPF = PartialFunction[EncryptionMethod, EncryptionModule]
  type SignPF       = PartialFunction[SignMethod, SignModule]

  def hashingAlgorithms: Set[String] = Set.empty
  def hashing: HashingPF             = PartialFunction.empty

  def encryptionAlgorithms: Set[String] = Set.empty
  def encryption: EncryptionPF          = PartialFunction.empty

  def signingAlgorithms: Set[String] = Set.empty
  def signing: SignPF                = PartialFunction.empty
}
