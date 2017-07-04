package com.karasiq.shadowcloud.actors.internal

import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRef}

import com.karasiq.shadowcloud.providers.SCModules
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps

private[actors] object StorageInstantiator {
  def apply(registry: SCModules): StorageInstantiator = {
    new StorageInstantiator(registry)
  }
}

private[actors] final class StorageInstantiator(modules: SCModules) extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    val plugin = modules.storage.storagePlugin(props)
    plugin.createStorage(storageId, props)
  }
}