package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom.DragEvent
import rx.{Rx, Var}

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.{File, FileId}
import com.karasiq.shadowcloud.webapp.components.common.OrderedTable
import com.karasiq.shadowcloud.webapp.components.common.OrderedTable.Column
import com.karasiq.shadowcloud.webapp.components.file.FileDownloadLink
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

object FolderFileList {
  def apply(files: Rx[Set[File]], flat: Boolean = true)(implicit context: AppContext, folderContext: FolderContext): FolderFileList = {
    new FolderFileList(files, flat)
  }
}

class FolderFileList(filesRx: Rx[Set[File]], flat: Boolean)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  val selectedFile = Var(None: Option[File])
  
  def renderTag(md: ModifierT*): TagT = {
    val files = Rx {
      val fileSet = filesRx()
      if (flat) FileVersions.toFlatDirectory(fileSet) else fileSet.toVector
    }

    val table = if (flat) {
      OrderedTable(files)(
        Column[File, String](context.locale.name, _.path.name, file ⇒ FileDownloadLink(file)(file.path.name)),
        Column[File, Long](context.locale.size, _.checksum.size, file ⇒ MemorySize.toString(file.checksum.size)),
        Column[File, Long](context.locale.modifiedDate, _.timestamp.lastModified, file ⇒ context.timeFormat.timestamp(file.timestamp.lastModified))
      )(f ⇒ fileRowModifiers(f), (f, s) ⇒ f.path.name.contains(s))
    } else {
      OrderedTable(files)(
        Column[File, FileId](context.locale.fileId, _.id, file ⇒ FileDownloadLink(file, useId = true)(file.id.toString)),
        Column[File, String](context.locale.name, _.path.name, _.path.name),
        Column[File, Long](context.locale.size, _.checksum.size, file ⇒ MemorySize.toString(file.checksum.size)),
        Column[File, Long](context.locale.modifiedDate, _.timestamp.lastModified, file ⇒ context.timeFormat.timestamp(file.timestamp.lastModified))
      )(f ⇒ fileRowModifiers(f), (f, s) ⇒ f.path.name.contains(s))
    }

    table.renderTag(md:_*)
  }

  protected def fileRowModifiers(file: File): Modifier = {
    val dragAndDropHandlers = Seq[Modifier](
      draggable,
      ondragstart := { (ev: DragEvent) ⇒
        DragAndDrop.addFolderContext(ev.dataTransfer)
        if (flat)
          DragAndDrop.addFilePath(ev.dataTransfer, file.path)
        else
          DragAndDrop.addFileHandle(ev.dataTransfer, file)
      }
    )

    Seq[Modifier](
      dragAndDropHandlers,
      TableRowStyle.active.styleClass.map(_.classIf(selectedFile.map(_.exists(_ == file)))),
      onclick := Callback.onClick(_ ⇒ selectedFile() = Some(file))
    )
  }
}
