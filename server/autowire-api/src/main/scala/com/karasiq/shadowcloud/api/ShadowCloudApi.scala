package com.karasiq.shadowcloud.api

import scala.concurrent.Future

import com.karasiq.shadowcloud.index.{Folder, Path}
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model.{FileId, RegionId}

trait ShadowCloudApi {
  def getFolder(regionId: RegionId, path: Path): Future[Folder]
  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition): Future[Seq[Metadata]]
}
