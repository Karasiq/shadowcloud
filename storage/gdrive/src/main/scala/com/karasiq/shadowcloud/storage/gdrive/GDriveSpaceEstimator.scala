package com.karasiq.shadowcloud.storage.gdrive

import java.time.{Duration, Instant}

import com.karasiq.common.memory.SizeUnit
import com.karasiq.gdrive.files.GDriveService
import com.karasiq.gdrive.files.GDriveService.TeamDriveId

import scala.concurrent.ExecutionContext
import scala.util.Try

private[gdrive] object GDriveSpaceEstimator {
  def apply(drive: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId) =
    new GDriveSpaceEstimator(drive)
}

private[gdrive] class GDriveSpaceEstimator(drive: GDriveService)(implicit ec: ExecutionContext, td: TeamDriveId) {
  @volatile
  private[this] var lastEstimated     = 0L
  private[this] var lastEstimatedTime = Instant.MIN // Before dinosaurs

  def usedSpace(): Long = {
    if (lastEstimatedTime.plus(Duration.ofMinutes(5)).compareTo(Instant.now()) < 0)
      this.estimate()

    this.lastEstimated
  }

  private[this] def estimate(): Unit = {
    lastEstimatedTime = Instant.now()
    ec.execute { () =>
      var size = 0L
      Try(drive.allFiles().foreach { file =>
        size += file.size
        if (size > lastEstimated) lastEstimated = size
      })
      lastEstimated = size
    }
  }
}
