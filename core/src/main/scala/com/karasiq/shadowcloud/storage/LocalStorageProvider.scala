package com.karasiq.shadowcloud.storage

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.ConfigProps
import com.karasiq.shadowcloud.providers.StorageProvider
import com.karasiq.shadowcloud.storage.files.FileStoragePlugin
import com.karasiq.shadowcloud.storage.inmem.InMemoryStoragePlugin

private[storage] final class LocalStorageProvider extends StorageProvider {
  override def storageTypes: Set[String] = {
    Set("memory", "files")
  }

  override def storages: StoragePF = {
    case props if props.storageType == "memory" ⇒
      new InMemoryStoragePlugin

    case props if props.storageType == "files" ⇒
      new FileStoragePlugin
  }

  def storageConfigs: StorageConfigPF = {
    case "memory" ⇒
      ConfigProps("type" → "memory")

    case "files" ⇒
      ConfigProps("type" → "files", "address.uri" → "file:///")
  }
}
