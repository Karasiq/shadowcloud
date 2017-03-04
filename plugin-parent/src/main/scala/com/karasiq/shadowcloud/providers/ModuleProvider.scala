package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod, HashingModule}
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

abstract class ModuleProvider {
  type StoragePF = PartialFunction[StorageProps, StoragePlugin]
  type HashingPF = PartialFunction[HashingMethod, HashingModule]
  type EncryptionPF = PartialFunction[EncryptionMethod, EncryptionModule]

  def name: String = ""
  def storages: StoragePF = PartialFunction.empty
  def hashing: HashingPF = PartialFunction.empty
  def encryption: EncryptionPF = PartialFunction.empty
}
