package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.file.FilePreview.PreviewVariants
import com.karasiq.shadowcloud.webapp.components.file.{FileDownloadLink, FilePreview}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.Blobs
import org.scalajs.dom.DragEvent
import rx._
import rx.async._
import scalaTags.all._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object PreviewsFileListItem {
  def apply(file: File, selectedFile: Var[Option[File]])(implicit context: AppContext, fc: FolderContext): PreviewsFileListItem = {
    new PreviewsFileListItem(file, selectedFile)
  }
}

class PreviewsFileListItem(file: File, selectedFile: Var[Option[File]])(implicit context: AppContext, fc: FolderContext)
    extends BootstrapHtmlComponent {
  lazy val previews = {
    FilePreview
      .getPreviews(fc.regionId, file.id)
      .toRx(PreviewVariants.empty)
  }

  def renderTag(md: ModifierT*): TagT = {
    val dragAndDropHandlers = Seq[Modifier](
      draggable,
      ondragstart := { ev: DragEvent ⇒
        DragAndDrop.addFolderContext(ev.dataTransfer)
        DragAndDrop.addFileHandle(ev.dataTransfer, file)
      }
    )
    val fileLink = FileDownloadLink(file)(file.path.name)

    GridSystem.row(
      GridSystem.col(3)(
        Rx[Frag](previews().image match {
          case Some(thumbnail) ⇒
            val blob     = Blobs.fromBytes(thumbnail.data.toArray)
            val imageUrl = Blobs.getUrl(blob)
            img(Bootstrap.image.responsive, Bootstrap.image.rounded, src := imageUrl, marginBottom := 10.px)

          case None ⇒
            AppIcons.file
        }),
        textAlign.center,
        verticalAlign.middle,
        lineHeight := 50.px
      ),
      GridSystem.col(9)(
        GridSystem.row(
          GridSystem.col(9)(Rx[Frag](if (selectedFile().contains(file)) b(fileLink) else span(fileLink, cursor.pointer))),
          GridSystem.col(3)(
            GridSystem.mkRow(MemorySize.toString(file.checksum.size)),
            GridSystem.mkRow(context.timeFormat.timestamp(file.timestamp.lastModified)),
            color.gray,
            // fontStyle.italic,
            fontWeight.bold,
            fontSizeAdjust := 0.4,
            lineHeight := 80.pct
          )
        ),
        Rx[Frag](previews().text match {
          case Some(text) ⇒
            GridSystem.mkRow(
              if (text.format == "text/html") raw(text.data) else text.data,
              maxHeight := 5.em,
              color.gray,
              // fontFamily := "monospace",
              fontSize := 12.px,
              // whiteSpace.`pre-wrap`,
              // lineHeight := 80.pct,
              overflow.hidden
            )

          case None ⇒
            ()
        })
      ),
      "tr-hover".addClass,
      // border := "solid 0.1px",
      marginTop := 8.px,
      marginBottom := 8.px,
      wordWrap.`break-word`,
      onclick := Callback.onClick(_ ⇒ selectedFile() = Some(file)),
      dragAndDropHandlers
    )
  }
}
