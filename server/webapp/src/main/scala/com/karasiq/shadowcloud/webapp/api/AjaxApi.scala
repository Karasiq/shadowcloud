package com.karasiq.shadowcloud.webapp.api

import scala.concurrent.ExecutionContext

import autowire._

import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.api.js.SCAjaxApiClient
import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.metadata.Metadata.Tag
import com.karasiq.shadowcloud.model.{FileId, RegionId}

object AjaxApi extends ShadowCloudApi with FileApi {
  private[api] val clientFactory = SCAjaxApiClient
  private[api] val encoding = clientFactory.encoding
  import encoding.implicits._

  private[this] val apiClient = clientFactory[ShadowCloudApi]
  private[this] implicit val ec: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def getFolder(regionId: RegionId, path: Path) = {
    apiClient.getFolder(regionId, path).call()
  }

  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Tag.Disposition) = {
    apiClient.getFileMetadata(regionId, fileId, disposition).call()
  }
}
