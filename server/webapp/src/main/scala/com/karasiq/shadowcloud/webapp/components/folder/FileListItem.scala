package com.karasiq.shadowcloud.webapp.components.folder

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import rx._
import rx.async._

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.file.FilePreview
import com.karasiq.shadowcloud.webapp.components.file.FilePreview.PreviewVariants
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.Blobs

object FileListItem {
  def apply(file: File, selectedFile: Var[Option[File]])(implicit context: AppContext, fc: FolderContext): FileListItem = {
    new FileListItem(file, selectedFile)
  }
}

class FileListItem(file: File, selectedFile: Var[Option[File]])(implicit context: AppContext, fc: FolderContext) extends BootstrapHtmlComponent {
  lazy val previews = {
    FilePreview.getPreviews(fc.regionId, file.id)
      .toRx(PreviewVariants.empty)
  }

  def renderTag(md: ModifierT*): TagT = {
    GridSystem.row(
      GridSystem.col(3).asDiv(
        Rx[Frag](previews().image match {
          case Some(thumbnail) ⇒
            val blob = Blobs.fromBytes(thumbnail.data.toArray)
            val imageUrl = Blobs.getUrl(blob)
            img(Bootstrap.image.responsive, Bootstrap.image.rounded, src := imageUrl)

          case None ⇒
            AppIcons.file
        }),
        textAlign.center,
        verticalAlign.middle,
        lineHeight := 50.px
      ),
      GridSystem.col(9).asDiv(
        GridSystem.mkRow(Rx[Frag](if (selectedFile().contains(file)) b(file.path.name) else file.path.name)),
        Rx[Frag](previews().text match {
          case Some(text) ⇒
            GridSystem.mkRow(
              if (text.format == "text/html") raw(text.data) else text.data,
              maxHeight := 5.em,
              color.gray,
              fontSizeAdjust := 0.4,
              lineHeight := 80.pct,
              overflow.hidden
            )

          case None ⇒
            ()
        })
      ),
      border := "solid 0.1px",
      marginTop := 2.px,
      onclick := Callback.onClick(_ ⇒ selectedFile() = Some(file))
    )
  }
}

