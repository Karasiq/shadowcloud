package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

abstract class StorageProvider extends ModuleProvider {
  type StoragePF = PartialFunction[StorageProps, StoragePlugin]

  def storageTypes: Set[String] = Set.empty
  def storages: StoragePF = PartialFunction.empty
}
