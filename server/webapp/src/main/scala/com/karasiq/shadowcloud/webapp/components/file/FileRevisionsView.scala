package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.{File, Path}
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.components.folder.FolderFileList
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.controllers.FileController
import com.karasiq.shadowcloud.webapp.utils.RxWithUpdate

object FileRevisionsView {
  def apply(path: Path)(implicit context: AppContext, folderContext: FolderContext, fileController: FileController): FileRevisionsView = {
    new FileRevisionsView(path)
  }

  private def getFilesRx(path: Path)(implicit context: AppContext, folderContext: FolderContext) = {
    RxWithUpdate(Set.empty[File])(_ ⇒ context.api.getFiles(folderContext.regionId, path, dropChunks = true, folderContext.scope.now))
  }
}

class FileRevisionsView(path: Path)(implicit context: AppContext, folderContext: FolderContext, _fileController: FileController)
    extends BootstrapHtmlComponent {

  lazy val filesRx = FileRevisionsView.getFilesRx(path)

  implicit lazy val fileController = FileController.inherit(
    _ ⇒ filesRx.update(),
    _ ⇒ filesRx.update(),
    (_, _) ⇒ filesRx.update(),
    (_, _) ⇒ filesRx.update()
  )(_fileController)

  lazy val fileList = {
    val fileList = FolderFileList(filesRx.toRx, flat = false)(context, folderContext, fileController)
    fileList.fileTable.setOrdering(fileList.fileTable.columns.now.last)
    fileList.fileTable.reverseOrdering() = true
    fileList.selectedFile.triggerLater(fileList.selectedFile.now.foreach { file ⇒
      Modal()
        .withTitle(context.locale.file)
        .withBody(FileView(file, useId = true)(context, folderContext, fileList.fileController))
        .withButtons(AppComponents.modalClose())
        .withDialogStyle(ModalDialogSize.large)
        .show(events = Map("hidden.bs.modal" → { () ⇒
          fileList.selectedFile() = None
        }))
    })
    fileList
  }

  def renderTag(md: ModifierT*): TagT = {
    fileList.renderTag(md: _*)
  }
}
