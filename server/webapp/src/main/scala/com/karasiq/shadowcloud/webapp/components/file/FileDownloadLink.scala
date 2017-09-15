package com.karasiq.shadowcloud.webapp.components.file

import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.{File, RegionId}
import com.karasiq.shadowcloud.webapp.context.AppContext

object FileDownloadLink {
  def apply(regionId: RegionId, file: File, useId: Boolean = false)(title: Modifier)
           (implicit context: AppContext): FileDownloadLink = {
    new FileDownloadLink(regionId, file, useId)(title)
  }
}

final class FileDownloadLink(regionId: RegionId, file: File, useId: Boolean)(title: Modifier)
                            (implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val downloadUrl = if (useId)
      context.api.downloadFileUrl(regionId, file.path, file.id)
    else
      context.api.downloadFileUrl(regionId, file.path)

    a(
      href := downloadUrl,
      target := "_blank",
      // attr("download") := file.path.name,
      title,
      md
    )
  }
}
