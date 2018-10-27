package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.file.FileView
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.{FileController, FolderController}
import com.karasiq.shadowcloud.webapp.utils.RxUtils

object FoldersPanel {
  def apply()(implicit appContext: AppContext,
              folderContext: FolderContext,
              folderController: FolderController,
              fileController: FileController): FoldersPanel = {
    new FoldersPanel
  }
}

class FoldersPanel(implicit context: AppContext,
                   folderContext: FolderContext,
                   folderController: FolderController,
                   fileController: FileController) extends BootstrapHtmlComponent {

  lazy val selectedFolder = RxUtils.getSelectedFolderRx

  lazy val folderTree = FolderTree(Path.root)
  lazy val folderFiles = FolderFileList(selectedFolder.map(_.files))

  def renderTag(md: ModifierT*): TagT = {
    val currentFileView = folderFiles.selectedFile.map[Frag] {
      case Some(file) ⇒ FileView(file)(context, folderContext, folderFiles.fileController)
      case None ⇒ ()
    }

    GridSystem.row(
      GridSystem.col.responsive(12, 3, 3, 2)(folderTree),
      GridSystem.col.responsive(12, 9, 5, 6)(folderFiles),
      GridSystem.col.responsive(12, 12, 4, 4)(currentFileView),
      md
    )
  }
}

