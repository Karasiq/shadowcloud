package com.karasiq.shadowcloud.providers

import akka.actor.{ActorContext, ActorSystem}
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.language.postfixOps

private[shadowcloud] object ModuleRegistry {
  def apply(config: Config): ModuleRegistry = {
    val registry = new ModuleRegistry()
    config.getStringList("providers").asScala.foreach { providerClassName ⇒
      val providerClass = Class.forName(providerClassName)
      val provider = providerClass.asSubclass(classOf[ModuleProvider]).newInstance()
      registry.register(provider)
    }
    registry
  }

  def apply(actorSystem: ActorSystem): ModuleRegistry = {
    apply(actorSystem.settings.config.getConfig("shadowcloud"))
  }

  def apply()(implicit context: ActorContext): ModuleRegistry = {
    apply(context.system)
  }

  private def getProviderName(provider: ModuleProvider): String = {
    val name = if (provider.name.isEmpty) provider.getClass.getName else provider.name
    require(name != null && name.nonEmpty)
    name
  }
}

private[shadowcloud] final class ModuleRegistry {
  private val providers = mutable.AnyRefMap.empty[String, ModuleProvider]
  private var storages = PartialFunction.empty[StorageProps, StoragePlugin]
  private var hashModules = PartialFunction.empty[HashingMethod, HashingModule]
  private var encModules = PartialFunction.empty[EncryptionMethod, EncryptionModule]

  def register(provider: ModuleProvider): Unit = {
    providers += ModuleRegistry.getProviderName(provider) → provider
    if (provider.storages != PartialFunction.empty) storages = provider.storages.orElse(storages)
    if (provider.hashing != PartialFunction.empty) hashModules = provider.hashing.orElse(hashModules)
    if (provider.encryption != PartialFunction.empty) encModules = provider.encryption.orElse(encModules)
  }

  def storagePlugin(storageProps: StorageProps): StoragePlugin = {
    if (storageProps.provider.isEmpty) {
      storages(storageProps)
    } else {
      providers(storageProps.provider).storages(storageProps)
    }
  }

  def hashingModule(method: HashingMethod): HashingModule = {
    if (method.provider.isEmpty) {
      hashModules(method)
    } else {
      providers(method.provider).hashing(method)
    }
  }

  def streamHashingModule(method: HashingMethod): StreamHashingModule = {
    hashingModule(method) match {
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
      providers(method.provider).encryption(method)
    }
  }
}
