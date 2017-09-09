package com.karasiq.shadowcloud.api

import scala.concurrent.Future

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.{FileAvailability, IndexScope}

trait ShadowCloudApi {
  // -----------------------------------------------------------------------
  // Folders
  // -----------------------------------------------------------------------
  def getFolder(regionId: RegionId, path: Path, scope: IndexScope = IndexScope.default): Future[Folder]
  def createFolder(regionId: RegionId, path: Path): Future[Folder]
  def deleteFolder(regionId: RegionId, path: Path): Future[Folder]

  // -----------------------------------------------------------------------
  // Files
  // -----------------------------------------------------------------------
  def getFiles(regionId: RegionId, path: Path, scope: IndexScope = IndexScope.default): Future[Set[File]]
  def getFileAvailability(regionId: RegionId, file: File): Future[FileAvailability]
  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition): Future[Seq[Metadata]]
}
