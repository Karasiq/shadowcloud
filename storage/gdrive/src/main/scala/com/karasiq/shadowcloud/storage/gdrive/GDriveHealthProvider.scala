package com.karasiq.shadowcloud.storage.gdrive

import scala.concurrent.{ExecutionContext, Future}

import com.karasiq.gdrive.files.GDriveService
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider

private[gdrive] object GDriveHealthProvider {
  def apply(service: GDriveService)(implicit ec: ExecutionContext): GDriveHealthProvider = {
    new GDriveHealthProvider(service)
  }
}

private[gdrive] class GDriveHealthProvider(service: GDriveService)(implicit ec: ExecutionContext) extends StorageHealthProvider {
  def health = {
    Future {
      val driveQuota = service.quota()
      val total = driveQuota.totalSize
      val used = driveQuota.usedSize
      val free = total - used
      StorageHealth.normalized(free, total, used)
    }
  }
}
