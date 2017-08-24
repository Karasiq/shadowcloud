package com.karasiq.shadowcloud.storage

import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRef}

import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.props.StorageProps

trait StoragePlugin {
  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef
}