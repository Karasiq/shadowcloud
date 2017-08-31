package com.karasiq.shadowcloud.webapp.api

import scala.concurrent.ExecutionContext

import autowire._

import com.karasiq.shadowcloud.api.{SCApiMeta, ShadowCloudApi}
import com.karasiq.shadowcloud.api.js.SCAjaxApiClient
import com.karasiq.shadowcloud.metadata.Metadata.Tag
import com.karasiq.shadowcloud.model.{FileId, Path, RegionId}

object AjaxApi extends ShadowCloudApi with FileApi with SCApiMeta {
  private[api] val clientFactory = SCAjaxApiClient

  type EncodingT = clientFactory.EncodingT
  val encoding = clientFactory.encoding
  val payloadContentType = clientFactory.payloadContentType

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
