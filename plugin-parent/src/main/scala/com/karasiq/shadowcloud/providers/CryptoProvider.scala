package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod, HashingModule}

import scala.language.postfixOps

abstract class CryptoProvider extends ModuleProvider {
  type HashingPF = PartialFunction[HashingMethod, HashingModule]
  type EncryptionPF = PartialFunction[EncryptionMethod, EncryptionModule]

  def hashingAlgorithms: Set[String] = Set.empty
  def hashing: HashingPF = PartialFunction.empty

  def encryptionAlgorithms: Set[String] = Set.empty
  def encryption: EncryptionPF = PartialFunction.empty
}
