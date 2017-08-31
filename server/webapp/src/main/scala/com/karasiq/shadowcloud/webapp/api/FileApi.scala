package com.karasiq.shadowcloud.webapp.api

import scala.concurrent.Future

import akka.util.ByteString
import org.scalajs.dom.ext.Ajax

import com.karasiq.shadowcloud.api.{SCApiEncoding, SCApiMeta, SCApiUtils}
import com.karasiq.shadowcloud.model.{File, Path, RegionId}
import com.karasiq.shadowcloud.webapp.utils.URLPath

trait FileApi { self: SCApiMeta ⇒
  import com.karasiq.shadowcloud.api.js.utils.AjaxUtils._

  def uploadFile(regionId: RegionId, path: Path, data: Ajax.InputData): Future[File] = {
    Ajax.post(uploadFileUrl(regionId, path), data, headers = Map("X-Requested-With" → SCApiUtils.requestedWith), responseType = "arraybuffer")
      .responseBytes
      .map(encoding.decodeFile)
  }

  def downloadFile(regionId: RegionId, path: Path): Future[ByteString] = {
    Ajax.get(downloadFileUrl(regionId, path), responseType = "arraybuffer")
      .responseBytes
  }

  def uploadFileUrl(regionId: RegionId, path: Path): String = {
    URLPath(_ / "upload" / regionId / SCApiEncoding.toUrlSafe(encoding.encodePath(path))).toString
  }

  def downloadFileUrl(regionId: RegionId, path: Path): String = {
    require(!path.isRoot, "Not a file")
    URLPath(_ / "download" / regionId / SCApiEncoding.toUrlSafe(encoding.encodePath(path)) / path.name).toString
  }
}
