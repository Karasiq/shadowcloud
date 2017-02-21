package com.karasiq.shadowcloud.storage.utils

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

class StorageInstantiator(plugins: Map[String, StoragePlugin]) extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    val plugin = plugins.getOrElse(props.storageType, throw new IllegalArgumentException(s"Storage plugin not found: ${props.storageType}"))
    plugin.createStorage(storageId, props)
  }
}

object StorageInstantiator {
  val default = new StorageInstantiator(Map(
    "memory" → StoragePlugin.memory,
    "files" → StoragePlugin.files
  ))
}

