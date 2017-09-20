package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom

import com.karasiq.shadowcloud.model.{Path, RegionId}
import com.karasiq.shadowcloud.webapp.components.file.FileView
import com.karasiq.shadowcloud.webapp.components.region.IndexScopeSelector
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.RxUtils

object FoldersPanel {
  def apply(regionId: RegionId)(implicit appContext: AppContext, folderContext: FolderContext): FoldersPanel = {
    new FoldersPanel(regionId)
  }
}

class FoldersPanel(regionId: RegionId)(implicit appContext: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val selectedFolderRx = RxUtils.getSelectedFolderRx
    val uploadForm = FormInput.file("File", onchange := Callback.onInput { input ⇒
      input.files.headOption.foreach { inputFile ⇒
        val parent = selectedFolderRx.now.path
        appContext.api.uploadFile(regionId, parent / inputFile.name, inputFile).foreach { file ⇒
          // TODO: Progress, reset input
          folderContext.update(parent)
          dom.window.alert(file.toString)
        }
      }
    })

    val scopeSelector = IndexScopeSelector.forContext(folderContext)
    val folderTree = FolderTree(Path.root)
    val folderView = FolderFileList(selectedFolderRx.map(_.files))

    div(
      GridSystem.row(
        GridSystem.col(6).asDiv(uploadForm),
        GridSystem.col(6).asDiv(scopeSelector)
      ),
      GridSystem.row(
        GridSystem.col(3).asDiv(folderTree),
        GridSystem.col(5).asDiv(folderView),
        GridSystem.col(4).asDiv(folderView.selectedFile.map[Frag] {
          case Some(file) ⇒ FileView(file)
          case None ⇒ ()
        })
      )
    )
  }
}

