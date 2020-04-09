package com.karasiq.shadowcloud.storage.gdrive

import com.karasiq.gdrive.files.GDriveService
import com.karasiq.gdrive.files.GDriveService.TeamDriveId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider

import scala.concurrent.{ExecutionContext, Future}

private[gdrive] object GDriveHealthProvider {
  def apply(service: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId): GDriveHealthProvider = {
    new GDriveHealthProvider(service)
  }
}

private[gdrive] class GDriveHealthProvider(service: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId) extends StorageHealthProvider {
  lazy val estimator = GDriveSpaceEstimator(service)

  def health = {
    if (td.enabled)
      Future.successful(StorageHealth.unlimited.copy(usedSpace = estimator.usedSpace()))
    else
      Future {
        val driveQuota = service.quota()
        val total      = if (driveQuota.totalSize == 0) Long.MaxValue else driveQuota.totalSize
        val used       = driveQuota.usedSize
        val free       = total - used
        StorageHealth.normalized(free, total, used)
      }
  }
}
