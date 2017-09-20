package com.karasiq.shadowcloud.providers

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

abstract class StorageProvider extends ModuleProvider {
  type StoragePF = PartialFunction[StorageProps, StoragePlugin]
  type StorageConfigPF = PartialFunction[String, SerializedProps]

  def storageTypes: Set[String] = Set.empty
  def storages: StoragePF = PartialFunction.empty
  def storageConfigs: StorageConfigPF
}
