package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.config.ProvidersConfig
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

private[shadowcloud] class StorageModuleRegistry(providers: ProvidersConfig[StorageProvider]) {
  private[this] val providerInstances = providers.instances
  private[this] val providerMap = providerInstances.toMap
  private[this] val storages = providerInstances
    .foldLeft(PartialFunction.empty[StorageProps, StoragePlugin]) { case (pf, (_, pr)) ⇒ pr.storages.orElse(pf) }

  def storagePlugin(storageProps: StorageProps): StoragePlugin = {
    if (storageProps.provider.isEmpty) {
      storages(storageProps)
    } else {
      providerMap(storageProps.provider).storages(storageProps)
    }
  }

  val storageTypes: Set[String] = {
    providerMap.values.flatMap(_.storageTypes).toSet
  }
}