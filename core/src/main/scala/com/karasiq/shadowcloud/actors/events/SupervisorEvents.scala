package com.karasiq.shadowcloud.actors.events

import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.storage.props.StorageProps

object SupervisorEvents {
  sealed trait Event
  case class RegionAdded(regionId: String, regionConfig: RegionConfig, active: Boolean) extends Event
  case class RegionDeleted(regionId: String) extends Event
  case class StorageAdded(storageId: String, props: StorageProps, active: Boolean) extends Event
  case class StorageDeleted(storageId: String) extends Event
  case class StorageRegistered(regionId: String, storageId: String) extends Event
  case class StorageUnregistered(regionId: String, storageId: String) extends Event
  case class StorageStateChanged(storageId: String, active: Boolean) extends Event
  case class RegionStateChanged(regionId: String, active: Boolean) extends Event
}
