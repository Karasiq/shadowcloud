package com.karasiq.shadowcloud.actors.events

import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.storage.props.StorageProps

object SupervisorEvents {
  sealed trait Event
  case class RegionAdded(regionId: RegionId, regionConfig: RegionConfig, active: Boolean) extends Event
  case class RegionDeleted(regionId: RegionId) extends Event
  case class StorageAdded(storageId: StorageId, props: StorageProps, active: Boolean) extends Event
  case class StorageDeleted(storageId: StorageId) extends Event
  case class StorageRegistered(regionId: RegionId, storageId: StorageId) extends Event
  case class StorageUnregistered(regionId: RegionId, storageId: StorageId) extends Event
  case class StorageStateChanged(storageId: StorageId, active: Boolean) extends Event
  case class RegionStateChanged(regionId: RegionId, active: Boolean) extends Event
}
