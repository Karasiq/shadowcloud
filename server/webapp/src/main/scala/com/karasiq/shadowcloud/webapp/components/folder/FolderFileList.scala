package com.karasiq.shadowcloud.webapp.components.folder

import org.scalajs.dom.DragEvent
import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.file.FileDownloadLink
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.FileController

object FolderFileList {
  def apply(files: Rx[Set[File]], flat: Boolean = true)
           (implicit context: AppContext, folderContext: FolderContext, fileController: FileController): FolderFileList = {
    new FolderFileList(files, flat)
  }
}

class FolderFileList(filesRx: Rx[Set[File]], flat: Boolean)(implicit context: AppContext,
                                                            folderContext: FolderContext,
                                                            _fileController: FileController) extends BootstrapHtmlComponent {

  val selectedFile = Var(None: Option[File])

  val selectedView = Var("table")

  implicit val fileController = FileController.inherit(
    onUpdateFile = (oldFile, newFile) ⇒ if (selectedFile.now.contains(oldFile)) selectedFile() = Some(newFile),
    onRenameFile = (file, newName) ⇒ if (selectedFile.now.contains(file)) selectedFile() = Some(file.copy(file.path.withName(newName)))
  )(_fileController)

  lazy val filesSeqRx = Rx {
    val fileSet = filesRx()
    if (flat) FileVersions.toFlatDirectory(fileSet) else fileSet.toVector
  }

  lazy val fileTable = {
    val baseTable = SortableTable.Builder[File]()
      .withRowModifiers(file ⇒ this.fileRowModifiers(file))
      .withFilter((file, str) ⇒ file.path.name.toLowerCase.contains(str.toLowerCase))

    val table = if (flat) {
      baseTable.withColumns(
        TableCol(context.locale.name, _.path.name, file ⇒ FileDownloadLink(file)(file.path.name)),
        TableCol(context.locale.size, _.checksum.size, file ⇒ MemorySize.toString(file.checksum.size)),
        TableCol(context.locale.modifiedDate, _.timestamp.lastModified, file ⇒ context.timeFormat.timestamp(file.timestamp.lastModified))
      )
    } else {
      baseTable.withColumns(
        TableCol(context.locale.fileId, _.id, file ⇒ FileDownloadLink(file, useId = true)(file.id.toString)),
        TableCol(context.locale.name, _.path.name, _.path.name),
        TableCol(context.locale.size, _.checksum.size, file ⇒ MemorySize.toString(file.checksum.size)),
        TableCol(context.locale.modifiedDate, _.timestamp.lastModified, file ⇒ context.timeFormat.timestamp(file.timestamp.lastModified))
      )
    }

    table.createTable(filesSeqRx)
  }

  lazy val previewsFileTable = PreviewsFileList(filesSeqRx, selectedFile)

  def renderTag(md: ModifierT*): TagT = {
    val viewSelectButton = Button(ButtonStyle.info)(AppIcons.changeView, context.locale.changeView, onclick := Callback.onClick(_ ⇒ changeListView()))
    val uploadForm = UploadForm()(context, folderContext, fileController)
    div(
      div(ButtonGroup(ButtonGroupSize.extraSmall, uploadForm.renderButton(), viewSelectButton)),
      Rx(div {
        if (selectedView() == "previews") {
          previewsFileTable.renderTag(md:_*)
        } else {
          fileTable.renderTag(md:_*)
        }
      })
    )

  }

  protected def changeListView(): Unit = {
    /* import org.scalajs.dom.window.sessionStorage
    val selectedView = Var((sessionStorage.getItem("file-list-view"): UndefOr[String]).toOption.getOrElse("table"))
    selectedView.triggerLater {
      sessionStorage.setItem("file-list-view", selectedView.now)
    }*/

    selectedView() = selectedView.now match {
      case "table" ⇒ "previews"
      case _ ⇒ "table"
    }
  }

  protected def fileRowModifiers(file: File): Modifier = {
    val dragAndDropHandlers = Seq[Modifier](
      draggable,
      ondragstart := { ev: DragEvent ⇒
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
