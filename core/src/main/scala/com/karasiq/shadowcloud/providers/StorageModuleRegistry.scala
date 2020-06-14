package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.config.{ConfigProps, ProvidersConfig, SerializedProps}
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] trait StorageModuleRegistry {
  def storageTypes: Set[String]
  def storagePlugin(storageProps: StorageProps): StoragePlugin
  def defaultConfig(storageType: String): SerializedProps
}

private[shadowcloud] object StorageModuleRegistry {
  def apply(providers: ProvidersConfig[StorageProvider])(implicit inst: ProviderInstantiator): StorageModuleRegistry = {
    new StorageModuleRegistryImpl(providers)
  }
}

private[shadowcloud] final class StorageModuleRegistryImpl(providers: ProvidersConfig[StorageProvider])(implicit inst: ProviderInstantiator)
    extends StorageModuleRegistry {

  private[this] val providerInstances = providers.instances
  private[this] val providerMap       = providerInstances.toMap

  private[this] val storages = providerInstances
    .foldLeft(PartialFunction.empty[StorageProps, StoragePlugin]) { case (pf, (_, pr)) ⇒ pr.storages.orElse(pf) }
  private[this] val storageConfigs = providerInstances
    .foldLeft(PartialFunction.empty[String, SerializedProps]) { case (pf, (_, pr)) ⇒ pr.storageConfigs.orElse(pf) }

  def storagePlugin(storageProps: StorageProps): StoragePlugin = {
    val pf = if (storageProps.provider.isEmpty) storages else providerMap(storageProps.provider).storages
    pf.applyOrElse(storageProps, (p: StorageProps) ⇒ throw new IllegalArgumentException(s"Invalid storage type or props: $p"))
  }

  val storageTypes: Set[String] = {
    providerMap.values.flatMap(_.storageTypes).toSet
  }

  def defaultConfig(storageType: String) = {
    storageConfigs.applyOrElse(storageType, (storageType: String) ⇒ ConfigProps("type" → storageType))
  }
}
