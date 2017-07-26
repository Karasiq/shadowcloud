package com.karasiq.shadowcloud.storage.replication

import akka.actor.ActorRef

import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage

object RegionStorageProvider {
  case class RegionStorage(id: String, dispatcher: ActorRef, health: StorageHealth, config: StorageConfig)
}

trait RegionStorageProvider {
  def getStorage(storageId: String): RegionStorage
  def storages: Iterable[RegionStorage]
}
