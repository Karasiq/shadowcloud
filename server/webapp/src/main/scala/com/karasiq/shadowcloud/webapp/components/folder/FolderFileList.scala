package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom.DragEvent
import rx.{Rx, Var}

import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.{File, Folder}
import com.karasiq.shadowcloud.utils.MemorySize
import com.karasiq.shadowcloud.webapp.components.file.FileDownloadLink
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

object FolderFileList {
  def apply(folder: Rx[Folder], flat: Boolean = true)(implicit context: AppContext, folderContext: FolderContext): FolderFileList = {
    new FolderFileList(folder, flat)
  }
}

class FolderFileList(folder: Rx[Folder], flat: Boolean)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
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
      val fileSet = folder().files
      val files = if (flat) {
        FileVersions.toFlatDirectory(fileSet)
      } else {
        fileSet.toSeq
      }
      val sortedFiles = files.sortBy(_.path.name)

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
            FileDownloadLink(folderContext.regionId, file)(file.path.name)
          )
        } else {
          Seq[Modifier](
            FileDownloadLink(folderContext.regionId, file, useId = true)(file.id.toString),
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
