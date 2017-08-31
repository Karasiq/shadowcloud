package com.karasiq.shadowcloud.webapp.components.file

import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.{File, RegionId}
import com.karasiq.shadowcloud.webapp.context.AppContext

object FileDownloadLink { // TODO: Download by UUID
  def apply(regionId: RegionId, file: File, title: Modifier)(implicit context: AppContext): FileDownloadLink = {
    new FileDownloadLink(regionId, file, title)
  }
}

final class FileDownloadLink(regionId: RegionId, file: File, title: Modifier)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    a(
      href := context.api.downloadFileUrl(regionId, file.path),
      target := "_blank",
      attr("download") := file.path.name,
      title,
      md
    )
  }
}
