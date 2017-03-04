package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.crypto.{EncryptionMethod, EncryptionModule, HashingMethod, HashingModule}
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

abstract class ModuleProvider {
  def storages: PartialFunction[StorageProps, StoragePlugin] = PartialFunction.empty
  def hashing: PartialFunction[HashingMethod, HashingModule] = PartialFunction.empty
  def encryption: PartialFunction[EncryptionMethod, EncryptionModule] = PartialFunction.empty
}
