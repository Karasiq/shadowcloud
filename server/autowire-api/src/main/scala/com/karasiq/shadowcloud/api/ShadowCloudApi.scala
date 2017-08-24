package com.karasiq.shadowcloud.api

import scala.concurrent.Future

import com.karasiq.shadowcloud.index.{Folder, Path}
import com.karasiq.shadowcloud.model.RegionId

trait ShadowCloudApi {
  def getFolder(regionId: RegionId, path: Path): Future[Folder]
}
