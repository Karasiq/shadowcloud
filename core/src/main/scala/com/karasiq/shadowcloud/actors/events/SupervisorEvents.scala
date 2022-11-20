package com.karasiq.shadowcloud.actors.events

import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.storage.props.StorageProps

object SupervisorEvents {
  sealed trait Event
  final case class RegionAdded(regionId: RegionId, regionConfig: RegionConfig, active: Boolean) extends Event
  final case class RegionDeleted(regionId: RegionId)                                            extends Event
  final case class StorageAdded(storageId: StorageId, props: StorageProps, active: Boolean)     extends Event
  final case class StorageDeleted(storageId: StorageId)                                         extends Event
  final case class StorageRegistered(regionId: RegionId, storageId: StorageId)                  extends Event
  final case class StorageUnregistered(regionId: RegionId, storageId: StorageId)                extends Event
  final case class StorageStateChanged(storageId: StorageId, active: Boolean)                   extends Event
  final case class RegionStateChanged(regionId: RegionId, active: Boolean)                      extends Event
}
