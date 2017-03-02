package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.providers.ModuleProvider
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

final class DefaultStoragesProvider extends ModuleProvider {
  override def storages: PartialFunction[StorageProps, StoragePlugin] = {
    case props if props.storageType == "memory" ⇒
      StoragePlugin.memory

    case props if props.storageType == "files" ⇒
      StoragePlugin.files
  }
}
