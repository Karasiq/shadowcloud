package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom.DragEvent
import rx.{Rx, Var}

import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.utils.MemorySize
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
    val heading = Rx {
      if (flat) {
        Seq[Modifier](
          context.locale.name,
          context.locale.size,
          context.locale.modifiedDate
        )
      } else {
        Seq[Modifier](
          context.locale.fileId,
          context.locale.name,
          context.locale.size,
          context.locale.modifiedDate
        )
      }
    }

    val rows = Rx {
      val sortedFiles = {
        val allFiles = filesRx()
        val files = if (flat) {
          FileVersions.toFlatDirectory(allFiles)
        } else {
          allFiles.toVector
        }
        files.sortBy(_.path.name)
      }

      sortedFiles.map { file ⇒
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

        val content1 = if (flat) {
          Seq[Modifier](
            FileDownloadLink(file)(file.path.name)
          )
        } else {
          Seq[Modifier](
            FileDownloadLink(file, useId = true)(file.id.toString),
            file.path.name
          )
        }

        val content2 = Seq[Modifier](
          MemorySize.toString(file.checksum.size),
          context.timeFormat.timestamp(file.timestamp.lastModified)
        )

        TableRow(
          content1 ++ content2,
          dragAndDropHandlers,
          TableRowStyle.active.styleClass.map(_.classIf(selectedFile.map(_.exists(_ == file)))),
          onclick := Callback.onClick(_ ⇒ selectedFile() = Some(file))
        )
      }
    }

    val table = PagedTable(heading, rows)
    table.renderTag(md:_*)
  }
}
