package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.file.FileView
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxUtils

object FoldersPanel {
  def apply()(implicit appContext: AppContext, folderContext: FolderContext): FoldersPanel = {
    new FoldersPanel
  }
}

class FoldersPanel(implicit appContext: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  private[this] lazy val selectedFolderRx = RxUtils.getSelectedFolderRx

  def renderTag(md: ModifierT*): TagT = {
    val folderTree = FolderTree(Path.root)
    val folderView = FolderFileList(selectedFolderRx.map(_.files))

    GridSystem.row(
      GridSystem.col(3).asDiv(folderTree),
      GridSystem.col(5).asDiv(folderView),
      GridSystem.col(4).asDiv(folderView.selectedFile.map[Frag] {
        case Some(file) ⇒ FileView(file)
        case None ⇒ ()
      }),
      md
    )
  }
}

