package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.config.ProvidersConfig
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] trait StorageModuleRegistry {
  def storageTypes: Set[String]
  def storagePlugin(storageProps: StorageProps): StoragePlugin
}

private[shadowcloud] object StorageModuleRegistry {
  def apply(providers: ProvidersConfig[StorageProvider])(implicit inst: ProviderInstantiator): StorageModuleRegistry = {
    new StorageModuleRegistryImpl(providers)
  }
}

private[shadowcloud] final class StorageModuleRegistryImpl(providers: ProvidersConfig[StorageProvider])
                                                          (implicit inst: ProviderInstantiator) extends StorageModuleRegistry {

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