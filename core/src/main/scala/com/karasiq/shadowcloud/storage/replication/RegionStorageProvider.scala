package com.karasiq.shadowcloud.storage.replication

import akka.actor.ActorRef

import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage

object RegionStorageProvider {
  case class RegionStorage(id: StorageId, props: StorageProps, config: StorageConfig, dispatcher: ActorRef, health: StorageHealth)
}

trait RegionStorageProvider {
  def getStorage(storageId: StorageId): RegionStorage
  def storages: Iterable[RegionStorage]
}
