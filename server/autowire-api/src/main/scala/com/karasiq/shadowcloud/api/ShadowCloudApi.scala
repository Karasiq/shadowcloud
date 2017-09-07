package com.karasiq.shadowcloud.api

import scala.concurrent.Future

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.FileAvailability

trait ShadowCloudApi {
  // -----------------------------------------------------------------------
  // Folders
  // -----------------------------------------------------------------------
  def getFolder(regionId: RegionId, path: Path): Future[Folder]
  def createFolder(regionId: RegionId, path: Path): Future[Folder]
  def deleteFolder(regionId: RegionId, path: Path): Future[Folder]

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
  def getFileById(regionId: RegionId, path: Path, fileId: FileId): Future[File]
  def getFileAvailability(regionId: RegionId, file: File): Future[FileAvailability]
  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition): Future[Seq[Metadata]]
}
