package com.karasiq.shadowcloud.exceptions

import com.karasiq.shadowcloud.model.{RegionId, StorageId}

sealed abstract class SupervisorException(message: String = null, cause: Throwable = null)
  extends SCException(message, cause)

object SupervisorException {
  final case class RegionNotFound(regionId: RegionId)
    extends SupervisorException("Region not found: " + regionId) with SCException.NotFound with SCException.RegionAssociated

  final case class StorageNotFound(storageId: StorageId)
    extends SupervisorException("Storage not found: " + storageId) with SCException.NotFound with SCException.StorageAssociated

  final case class IllegalRegionState(regionId: RegionId, cause: Throwable = null)
    extends SupervisorException("Illegal region state: " + regionId) with SCException.WrappedError with SCException.RegionAssociated

  final case class IllegalStorageState(storageId: StorageId, cause: Throwable = null)
    extends SupervisorException("Illegal storage state: " + storageId) with SCException.WrappedError with SCException.StorageAssociated
}
