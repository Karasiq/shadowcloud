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
      GridSystem.col.responsive(12, 3, 3, 2).asDiv(folderTree),
      GridSystem.col.responsive(12, 9, 5, 6).asDiv(folderView),
      GridSystem.col.responsive(12, 12, 4, 4).asDiv(folderView.selectedFile.map[Frag] {
        case Some(file) ⇒ FileView(file)(appContext, folderContext, folderView.controller)
        case None ⇒ ()
      }),
      md
    )
  }
}

