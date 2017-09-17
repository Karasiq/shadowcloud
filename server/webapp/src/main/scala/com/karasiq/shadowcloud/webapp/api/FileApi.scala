package com.karasiq.shadowcloud.webapp.api

import scala.concurrent.Future

import akka.util.ByteString
import org.scalajs.dom.ext.Ajax

import com.karasiq.shadowcloud.api.{SCApiEncoding, SCApiMeta, SCApiUtils}
import com.karasiq.shadowcloud.model.{File, FileId, Path, RegionId}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.webapp.utils.URLPath

trait FileApi { self: SCApiMeta ⇒
  import com.karasiq.shadowcloud.api.js.utils.AjaxUtils._

  def uploadFile(regionId: RegionId, path: Path, data: Ajax.InputData): Future[File] = {
    Ajax.post(uploadFileUrl(regionId, path), data, headers = Map("X-Requested-With" → SCApiUtils.requestedWith), responseType = "arraybuffer")
      .responseBytes
      .map(encoding.decodeFile)
  }

  def downloadFile(regionId: RegionId, path: Path, scope: IndexScope): Future[ByteString] = {
    Ajax.get(mostRecentFileUrl(regionId, path, scope), responseType = "arraybuffer")
      .responseBytes
  }

  def uploadFileUrl(regionId: RegionId, path: Path): String = {
    URLPath(_ / "upload" / regionId / SCApiEncoding.toUrlSafe(encoding.encodePath(path))).toString
  }

  def mostRecentFileUrl(regionId: RegionId, path: Path, scope: IndexScope = IndexScope.default): String = {
    require(!path.isRoot, "Not a file")
    val baseUrl = URLPath(_ / "download" / regionId / SCApiEncoding.toUrlSafe(encoding.encodePath(path)) / path.name)

    val scopedUrl = if (scope == IndexScope.default)
      baseUrl
    else
      baseUrl.withQuery("scope", SCApiEncoding.toUrlSafe(encoding.encodeScope(scope)))

    scopedUrl.toString
  }

  def fileUrl(regionId: RegionId, path: Path, fileId: FileId, scope: IndexScope = IndexScope.default): String = {
    require(!path.isRoot, "Not a file")
    val baseUrl = URLPath(_ / "download" / regionId / SCApiEncoding.toUrlSafe(encoding.encodePath(path)) / path.name)
      .withQuery("file-id", fileId.toString)

    val scopedUrl = if (scope == IndexScope.default)
      baseUrl
    else
      baseUrl.withQuery("scope", SCApiEncoding.toUrlSafe(encoding.encodeScope(scope)))
      
    scopedUrl.toString
  }
}
