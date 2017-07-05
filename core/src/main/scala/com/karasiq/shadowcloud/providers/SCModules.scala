package com.karasiq.shadowcloud.providers

import scala.language.postfixOps

import com.karasiq.shadowcloud.config.{ProvidersConfig, SCConfig}
import com.karasiq.shadowcloud.metadata.MetadataProvider
import com.karasiq.shadowcloud.utils.ProviderInstantiator

private[shadowcloud] object SCModules {
  def apply(config: SCConfig)(implicit inst: ProviderInstantiator): SCModules = {
    new SCModules(
      config.storage.providers,
      config.crypto.providers,
      config.metadata.providers
    )
  }
}

private[shadowcloud] class SCModules(_storages: ProvidersConfig[StorageProvider],
                                     _crypto: ProvidersConfig[CryptoProvider],
                                     _metadata: ProvidersConfig[MetadataProvider])(implicit inst: ProviderInstantiator) {

  val storage = new StorageModuleRegistry(_storages)
  val crypto = new CryptoModuleRegistry(_crypto)
  val metadata = new MetadataModuleRegistry(_metadata)

  override def toString: String = {
    s"SCModules(storages = [${storage.storageTypes.mkString(", ")}, hashes = [${crypto.hashingAlgorithms.mkString(", ")}], encryption = [${crypto.encryptionAlgorithms.mkString(", ")}], metadata = [${metadata.metadataPlugins.mkString(", ")}])"
  }
}