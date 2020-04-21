package com.karasiq.shadowcloud.providers



import com.karasiq.shadowcloud.config.{ProvidersConfig, SCConfig}
import com.karasiq.shadowcloud.metadata.MetadataProvider
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] trait SCModules {
  def storage: StorageModuleRegistry
  def crypto: CryptoModuleRegistry
  def metadata: MetadataModuleRegistry
}

private[shadowcloud] object SCModules {
  def apply(config: SCConfig)(implicit inst: ProviderInstantiator): SCModules = {
    new SCModulesImpl(
      config.storage.providers,
      config.crypto.providers,
      config.metadata.providers
    )
  }
}

private[shadowcloud] final class SCModulesImpl(_storages: ProvidersConfig[StorageProvider],
                                               _crypto: ProvidersConfig[CryptoProvider],
                                               _metadata: ProvidersConfig[MetadataProvider])
                                              (implicit inst: ProviderInstantiator) extends SCModules {

  val storage = StorageModuleRegistry(_storages)
  val crypto = CryptoModuleRegistry(_crypto)
  val metadata = MetadataModuleRegistry(_metadata)

  override def toString: String = {
    s"SCModules(storages = [${storage.storageTypes.mkString(", ")}, hashes = [${crypto.hashingAlgorithms.mkString(", ")}], encryption = [${crypto.encryptionAlgorithms.mkString(", ")}], metadata = [${metadata.metadataPlugins.mkString(", ")}])"
  }
}
