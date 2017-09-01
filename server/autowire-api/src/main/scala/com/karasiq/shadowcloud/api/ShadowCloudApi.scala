package com.karasiq.shadowcloud.api

import scala.concurrent.Future

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model.{FileId, Folder, Path, RegionId}

trait ShadowCloudApi {
  def getFolder(regionId: RegionId, path: Path): Future[Folder]
  def createFolder(regionId: RegionId, path: Path): Future[Folder]
  def deleteFolder(regionId: RegionId, path: Path): Future[Folder]
  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition): Future[Seq[Metadata]]
}
