package com.karasiq.shadowcloud.webapp.api

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.Blob
import play.api.libs.json.{Json, Reads}

import com.karasiq.shadowcloud.index.{File, Path}
import com.karasiq.shadowcloud.webapp.utils.URLUtils.URLPath

object FileApi {
  import com.karasiq.shadowcloud.api.SCJsonEncoders._

  private[this] def decodeResponse[R: Reads](r: dom.XMLHttpRequest): R = {
    Json.parse(r.responseText).as[R]
  }

  def uploadFile(regionId: String, path: Path, data: Ajax.InputData): Future[File] = {
    Ajax.post(uploadFileUrl(regionId, path), data).map(decodeResponse[File])
  }

  def downloadFile(regionId: String, path: Path): Future[Blob] = {
    Ajax.get(downloadFileUrl(regionId, path), responseType = "blob")
      .filter(_.status == 200)
      .map(_.response.asInstanceOf[Blob])
  }

  private[this] def uploadFileUrl(regionId: String, path: Path): String = {
    URLPath()
      .withPath(_ / "upload" / regionId)
      .withQueryJson("path", path)
      .toString
  }

  private[this] def downloadFileUrl(regionId: String, path: Path): String = {
    require(!path.isRoot, "Not a file")
    URLPath()
      .withPath(_ / "download" / regionId / path.name)
      .withQueryJson("path", path)
      .toString
  }
}
