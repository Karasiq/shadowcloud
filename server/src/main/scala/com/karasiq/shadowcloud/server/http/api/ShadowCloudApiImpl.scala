package com.karasiq.shadowcloud.server.http.api

import scala.concurrent.Future

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.index.{Folder, Path}

private[server] final class ShadowCloudApiImpl(sc: ShadowCloudExtension) extends ShadowCloudApi {
  import sc.implicits.executionContext

  def getFolder(regionId: String, path: Path): Future[Folder] = {
    sc.ops.region.getFolder(regionId, path).map(_.withoutChunks)
  }
}
