package com.karasiq.shadowcloud.providers

import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.collection.mutable
import scala.language.postfixOps

private[shadowcloud] object ModuleRegistry {
  def empty: ModuleRegistry = {
    new ModuleRegistry
  }

  def apply(config: AppConfig): ModuleRegistry = {
    val registry = this.empty
    for ((pName, pClass) ← config.storage.providers.toMap) {
      registry.register(pName, pClass.newInstance())
    }
    for ((pName, pClass) ← config.crypto.providers.toMap) {
      registry.register(pName, pClass.newInstance())
    }
    registry
  }
}

private[shadowcloud] final class ModuleRegistry extends StorageModuleRegistry with CryptoModuleRegistry {
  override def toString: String = {
    s"ModuleRegistry(storages = [${storageTypes.mkString(", ")}, hashes = [${hashingAlgorithms.mkString(", ")}], encryption = [${encryptionAlgorithms.mkString(", ")}])"
  }
}

private[shadowcloud] sealed trait StorageModuleRegistry {
  private[this] val storageProviders = mutable.AnyRefMap.empty[String, StorageProvider]
  private[this] var storages = PartialFunction.empty[StorageProps, StoragePlugin]

  def register(providerName: String, provider: StorageProvider): Unit = {
    require(providerName.nonEmpty, "Provider name is empty")
    if (provider.storages != PartialFunction.empty) storages = provider.storages.orElse(storages)
    storageProviders += providerName → provider
  }

  def register(provider: StorageProvider): Unit = {
    register(provider.defaultName, provider)
  }

  def storagePlugin(storageProps: StorageProps): StoragePlugin = {
    if (storageProps.provider.isEmpty) {
      storages(storageProps)
    } else {
      storageProviders(storageProps.provider).storages(storageProps)
    }
  }

  def storageTypes: Set[String] = {
    storageProviders.values.flatMap(_.storageTypes).toSet
  }
}

private[shadowcloud] sealed trait CryptoModuleRegistry {
  private[this] val cryptoProviders = mutable.AnyRefMap.empty[String, CryptoProvider]
  private[this] var hashModules = PartialFunction.empty[HashingMethod, HashingModule]
  private[this] var encModules = PartialFunction.empty[EncryptionMethod, EncryptionModule]

  def register(providerName: String, provider: CryptoProvider): Unit = {
    require(providerName.nonEmpty, "Provider name is empty")
    cryptoProviders += providerName → provider

    if (provider.hashing != PartialFunction.empty) hashModules = provider.hashing.orElse(hashModules)
    if (provider.encryption != PartialFunction.empty) encModules = provider.encryption.orElse(encModules)
  }

  def register(provider: CryptoProvider): Unit = {
    register(provider.defaultName, provider)
  }

  def hashingModule(method: HashingMethod): HashingModule = {
    if (method.provider.isEmpty) {
      hashModules(method)
    } else {
      cryptoProviders(method.provider).hashing(method)
    }
  }

  def streamHashingModule(method: HashingMethod): StreamHashingModule = {
    hashingModule(method.copy(stream = true)) match {
      case m: StreamHashingModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream hashing module required")
    }
  }

  def encryptionModule(method: EncryptionMethod): EncryptionModule = {
    if (method.provider.isEmpty) {
      encModules(method)
    } else {
      cryptoProviders(method.provider).encryption(method)
    }
  }

  def streamEncryptionModule(method: EncryptionMethod): StreamEncryptionModule = {
    encryptionModule(method.copy(stream = true)) match {
      case m: StreamEncryptionModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream encryption module required")
    }
  }


  def hashingAlgorithms: Set[String] = {
    cryptoProviders.values.flatMap(_.hashingAlgorithms).toSet
  }

  def encryptionAlgorithms: Set[String] = {
    cryptoProviders.values.flatMap(_.encryptionAlgorithms).toSet
  }
}
