package com.karasiq.shadowcloud.storage.gdrive

import com.karasiq.gdrive.files.GDriveService
import com.karasiq.gdrive.files.GDriveService.TeamDriveId
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.concurrent.{ExecutionContext, Future}

private[gdrive] object GDriveHealthProvider {
  def apply(service: GDriveService, props: StorageProps)(implicit ec: ExecutionContext, td: TeamDriveId): GDriveHealthProvider = {
    new GDriveHealthProvider(service, props)
  }
}

private[gdrive] class GDriveHealthProvider(service: GDriveService, props: StorageProps)(implicit ec: ExecutionContext, td: TeamDriveId)
    extends StorageHealthProvider {
  lazy val estimator = GDriveSpaceEstimator(service, StoragePluginBuilder.getRootPath(props))

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
