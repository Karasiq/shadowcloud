package com.karasiq.shadowcloud.webapp.api

import scala.concurrent.{ExecutionContext, Future}

import autowire._

import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.api.js.JsonApiClient
import com.karasiq.shadowcloud.index.{Folder, Path}

object AutowireApi extends ShadowCloudApi {
  import com.karasiq.shadowcloud.api.SCJsonEncoders._
  private[this] val jsonClient = JsonApiClient[ShadowCloudApi]
  private[this] implicit val ec: ExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def getFolder(regionId: String, path: Path): Future[Folder] = {
    jsonClient.getFolder(regionId, path).call()
  }
}
