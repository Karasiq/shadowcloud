package com.karasiq.shadowcloud.webapp.components.file

import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.index.File
import com.karasiq.shadowcloud.webapp.api.AjaxApi

object FileDownloadLink { // TODO: Download by UUID
  def apply(regionId: RegionId, file: File): FileDownloadLink = {
    new FileDownloadLink(regionId, file)
  }
}

final class FileDownloadLink(regionId: RegionId, file: File) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    a(
      href := AjaxApi.downloadFileUrl(regionId, file.path),
      target := "_blank",
      attr("download") := file.path.name,
      md
    )
  }
}
