package com.karasiq.shadowcloud.webapp.components.file

import rx.async._

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.components.folder.FolderFileList
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext

object FileRevisionsView {
  def apply(path: Path)(implicit context: AppContext, folderContext: FolderContext): FileRevisionsView = {
    new FileRevisionsView(path)
  }

  private def getFilesRx(path: Path)(implicit context: AppContext, folderContext: FolderContext) = {
    context.api.getFiles(folderContext.regionId, path, dropChunks = true, folderContext.scope.now).toRx(Set.empty)
  }
}

class FileRevisionsView(path: Path)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val fileList = FolderFileList(FileRevisionsView.getFilesRx(path), flat = false)
    fileList.selectedFile.triggerLater(fileList.selectedFile.now.foreach { file ⇒
      Modal()
        .withTitle(context.locale.file)
        .withBody(FileView(file, useId = true)(context, folderContext, fileList.controller))
        .withButtons(AppComponents.modalClose())
        .withDialogStyle(ModalDialogSize.large)
        .show(events = Map("hidden.bs.modal" → { () ⇒
          fileList.selectedFile() = None 
        }))
    })
    fileList
  }
}

