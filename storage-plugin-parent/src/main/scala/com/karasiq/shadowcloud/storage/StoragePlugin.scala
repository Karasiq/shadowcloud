package com.karasiq.shadowcloud.storage

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

trait StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef
}