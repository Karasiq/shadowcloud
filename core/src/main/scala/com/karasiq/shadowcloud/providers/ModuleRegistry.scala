package com.karasiq.shadowcloud.providers

import akka.actor.{ActorContext, ActorSystem}
import com.karasiq.shadowcloud.crypto._
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.typesafe.config.Config

import scala.collection.JavaConverters._
import scala.language.postfixOps

object ModuleRegistry {
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
}

final class ModuleRegistry {
  private var storages = PartialFunction.empty[StorageProps, StoragePlugin]
  private var hashModules = PartialFunction.empty[HashingMethod, HashingModule]
  private var encModules = PartialFunction.empty[EncryptionMethod, EncryptionModule]

  def register(provider: ModuleProvider): Unit = {
    if (provider.storages != PartialFunction.empty) storages = provider.storages.orElse(storages)
    if (provider.hashing != PartialFunction.empty) hashModules = provider.hashing.orElse(hashModules)
    if (provider.encryption != PartialFunction.empty) encModules = provider.encryption.orElse(encModules)
  }

  def storagePlugin(storageProps: StorageProps): StoragePlugin = {
    storages(storageProps)
  }

  def hashingModule(hashingMethod: HashingMethod): HashingModule = {
    hashModules(hashingMethod)
  }

  def streamHashingModule(hashingMethod: HashingMethod): StreamHashingModule = {
    hashModules(hashingMethod) match {
      case m: StreamHashingModule ⇒
        m

      case _ ⇒
        throw new IllegalArgumentException("Stream hashing module required")
    }
  }

  def encryptionModule(encryptionMethod: EncryptionMethod): EncryptionModule = {
    encModules(encryptionMethod)
  }
}
