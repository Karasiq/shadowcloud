package com.karasiq.shadowcloud.webapp.components.file



import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxUtils
import rx.Rx
import scalaTags.all._

object FileDownloadLink {
  def apply(file: File, useId: Boolean = false)(title: Modifier*)
           (implicit context: AppContext, folderContext: FolderContext): FileDownloadLink = {
    new FileDownloadLink(file, useId)(title)
  }
}

final class FileDownloadLink(file: File, useId: Boolean)(title: Modifier*)
                            (implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {

  def renderTag(md: ModifierT*): TagT = {
    val downloadUrl = RxUtils.getDownloadLinkRx(file, useId)
    a(
      Rx(href := downloadUrl()).auto,
      target := "_blank",
      // attr("download") := file.path.name,
      title,
      md
    )
  }
}
