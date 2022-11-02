package com.karasiq.shadowcloud.model.utils

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.model.utils.RegionStateReport.{RegionStatus, StorageStatus}

@SerialVersionUID(0L)
final case class RegionStateReport(regions: Map[RegionId, RegionStatus], storages: Map[StorageId, StorageStatus])

object RegionStateReport {
  @SerialVersionUID(0L)
  final case class RegionStatus(
      regionId: RegionId,
      regionConfig: SerializedProps = SerializedProps.empty,
      suspended: Boolean = false,
      storages: Set[String] = Set.empty
  )

  @SerialVersionUID(0L)
  final case class StorageStatus(
      storageId: StorageId,
      storageProps: SerializedProps = SerializedProps.empty,
      suspended: Boolean = false,
      regions: Set[String] = Set.empty
  )

  val empty = new RegionStateReport(Map.empty, Map.empty)
}
