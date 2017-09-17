package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.folder.FolderFileList
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxUtils

object FileRevisionsView {
  def apply(path: Path)(implicit context: AppContext, folderContext: FolderContext): FileRevisionsView = {
    new FileRevisionsView(path)
  }
}

class FileRevisionsView(path: Path)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  private[this] lazy val filesRx = RxUtils.toFilesRx(path)

  def renderTag(md: ModifierT*): TagT = {
    val fileList = FolderFileList(filesRx, flat = false)
    fileList.selectedFile.triggerLater(fileList.selectedFile.now.foreach { file ⇒
      Modal()
        .withTitle(context.locale.file)
        .withBody(FileView(file, useId = true))
        .withButtons(Modal.closeButton(context.locale.close))
        .show(events = Map("hidden.bs.modal" → { () ⇒
          fileList.selectedFile() = None 
        }))
    })
    fileList
  }
}

