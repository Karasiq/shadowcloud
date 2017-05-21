package com.karasiq.shadowcloud.providers

import java.security.NoSuchAlgorithmException

import scala.collection.mutable
import scala.language.postfixOps

import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

private[shadowcloud] object ModuleRegistry {
  def empty: ModuleRegistry = {
    new ModuleRegistry
  }

  def apply(config: AppConfig): ModuleRegistry = {
    fromNamedProviders(
      config.storage.providers.instances,
      config.crypto.providers.instances
    )
  }

  def fromNamedProviders(storages: Seq[(String, StorageProvider)],
                         crypto: Seq[(String, CryptoProvider)]): ModuleRegistry = {
    val registry = this.empty
    for ((pName, pInstance) ← storages) registry.register(pName, pInstance)
    for ((pName, pInstance) ← crypto) registry.register(pName, pInstance)
    registry
  }

  def fromProviders(storages: Seq[StorageProvider], crypto: Seq[CryptoProvider]): ModuleRegistry = {
    fromNamedProviders(storages.map(p ⇒ (p.defaultName, p)), crypto.map(p ⇒ (p.defaultName, p)))
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

  private[providers] def register(providerName: String, provider: StorageProvider): Unit = {
    require(providerName.nonEmpty, "Provider name is empty")
    if (provider.storages != PartialFunction.empty) storages = provider.storages.orElse(storages)
    storageProviders += providerName → provider
  }

  private[providers] def register(provider: StorageProvider): Unit = {
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
  private[this] var signModules = PartialFunction.empty[SignMethod, SignModule]

  private[providers] def register(providerName: String, provider: CryptoProvider): Unit = {
    require(providerName.nonEmpty, "Provider name is empty")
    cryptoProviders += providerName → provider

    if (provider.hashing != PartialFunction.empty) hashModules = provider.hashing.orElse(hashModules)
    if (provider.encryption != PartialFunction.empty) encModules = provider.encryption.orElse(encModules)
    if (provider.sign != PartialFunction.empty) signModules = provider.sign.orElse(signModules)
  }

  private[providers] def register(provider: CryptoProvider): Unit = {
    register(provider.defaultName, provider)
  }

  def hashingModule(method: HashingMethod): HashingModule = {
    val pf = if (method.provider.isEmpty) {
      hashModules
    } else {
      cryptoProviders(method.provider).hashing
    }
    moduleFromPF(pf, method)
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
    val pf = if (method.provider.isEmpty) {
      encModules
    } else {
      cryptoProviders(method.provider).encryption
    }
    moduleFromPF(pf, method)
  }

  def streamEncryptionModule(method: EncryptionMethod): StreamEncryptionModule = {
    encryptionModule(method.copy(stream = true)) match {
      case m: StreamEncryptionModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream encryption module required")
    }
  }

  def signModule(method: SignMethod): SignModule = {
    val pf = if (method.provider.isEmpty) {
      signModules
    } else {
      cryptoProviders(method.provider).sign
    }
    moduleFromPF(pf, method)
  }

  def streamSignModule(method: SignMethod): StreamSignModule = {
    signModule(method.copy(stream = true)) match {
      case m: StreamSignModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream signature module required")
    }
  }

  def hashingAlgorithms: Set[String] = {
    cryptoProviders.values.flatMap(_.hashingAlgorithms).toSet
  }

  def encryptionAlgorithms: Set[String] = {
    cryptoProviders.values.flatMap(_.encryptionAlgorithms).toSet
  }

  def signAlgorithms: Set[String] = {
    cryptoProviders.values.flatMap(_.signAlgorithms).toSet
  }

  @inline 
  private[this] def moduleFromPF[T <: CryptoMethod, R](pf: PartialFunction[T, R], method: T): R = {
    if (pf.isDefinedAt(method)) pf(method) else throw new NoSuchAlgorithmException(method.algorithm)
  }
}
