package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.providers.ModuleProvider
import com.karasiq.shadowcloud.storage.files.FileStoragePlugin
import com.karasiq.shadowcloud.storage.inmem.InMemoryStoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

final class DefaultStoragesProvider extends ModuleProvider {
  override def storages: PartialFunction[StorageProps, StoragePlugin] = {
    case props if props.storageType == "memory" ⇒
      new InMemoryStoragePlugin

    case props if props.storageType == "files" ⇒
      new FileStoragePlugin
  }
}
