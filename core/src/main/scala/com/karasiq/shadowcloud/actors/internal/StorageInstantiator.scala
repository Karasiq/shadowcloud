package com.karasiq.shadowcloud.actors.internal

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

private[actors] object StorageInstantiator {
  def apply(registry: ModuleRegistry): StorageInstantiator = {
    new StorageInstantiator(registry)
  }
}

private[actors] final class StorageInstantiator(registry: ModuleRegistry) extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    val plugin = registry.storagePlugin(props)
    plugin.createStorage(storageId, props)
  }
}