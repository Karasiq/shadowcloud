package com.karasiq.shadowcloud.storage.replication

import akka.actor.ActorRef

import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.replication.StorageStatusProvider.StorageStatus

object StorageStatusProvider {
  case class StorageStatus(id: String, dispatcher: ActorRef, health: StorageHealth, config: StorageConfig)
}

trait StorageStatusProvider {
  def getStorage(storageId: String): StorageStatus
  def storages: Iterable[StorageStatus]
}
