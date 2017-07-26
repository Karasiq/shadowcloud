package com.karasiq.shadowcloud.storage.replication

import akka.actor.ActorRef

import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.replication.RegionStorageProvider.RegionStorage

object RegionStorageProvider {
  case class RegionStorage(id: String, props: StorageProps, config: StorageConfig,
                           dispatcher: ActorRef, health: StorageHealth)
}

trait RegionStorageProvider {
  def getStorage(storageId: String): RegionStorage
  def storages: Iterable[RegionStorage]
}
