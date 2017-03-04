package com.karasiq.shadowcloud.storage

import com.karasiq.shadowcloud.providers.ModuleProvider
import com.karasiq.shadowcloud.storage.files.FileStoragePlugin
import com.karasiq.shadowcloud.storage.inmem.InMemoryStoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

private[storage] final class LocalStorageProvider extends ModuleProvider {
  override val name = "local"

  override def storages: PartialFunction[StorageProps, StoragePlugin] = {
    case props if props.storageType == "memory" ⇒
      new InMemoryStoragePlugin

    case props if props.storageType == "files" ⇒
      new FileStoragePlugin
  }
}
