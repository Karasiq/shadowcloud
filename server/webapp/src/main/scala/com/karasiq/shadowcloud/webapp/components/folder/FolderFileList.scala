package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.index.{File, Folder}
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.utils.MemorySize
import com.karasiq.shadowcloud.webapp.components.file.FileDownloadLink
import com.karasiq.shadowcloud.webapp.context.AppContext

object FolderFileList {
  def apply(regionId: RegionId, folder: Rx[Folder])(implicit context: AppContext): FolderFileList = {
    new FolderFileList(regionId, folder)
  }
}

class FolderFileList(regionId: RegionId, folder: Rx[Folder])(implicit context: AppContext) extends BootstrapHtmlComponent {
  val selectedFile = Var(None: Option[File])

  def renderTag(md: ModifierT*): TagT = {
    val heading = Seq[Modifier](
      context.locale.fileId,
      context.locale.name,
      context.locale.size,
      context.locale.modifiedDate
    )

    val rows = folder.map(_.files.toSeq.sortBy(_.path.name).map { file ⇒
      val content = Seq[Modifier](
        FileDownloadLink(regionId, file)(file.id.toString),
        file.path.name,
        MemorySize.toString(file.checksum.size),
        context.timeFormat.timestamp(file.timestamp.lastModified)
      )

      TableRow(
        content,
        TableRowStyle.active.styleClass.map(_.classIf(selectedFile.map(_.exists(_ == file)))),
        onclick := Callback.onClick(_ ⇒ selectedFile() = Some(file))
      )
    })

    val table = PagedTable(Rx(heading), rows)
    table.renderTag(md:_*)
  }
}
