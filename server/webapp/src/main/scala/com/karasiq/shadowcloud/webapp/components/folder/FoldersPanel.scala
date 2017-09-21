package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

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
    val progressBars = div().render
    // attr("directory").empty, attr("webkitdirectory").empty
    val uploadInput = FormInput.file(appContext.locale.file, multiple, onchange := Callback.onInput { input ⇒
      input.files.foreach { inputFile ⇒
        val parent = selectedFolderRx.now.path
        val (progress, future) = appContext.api.uploadFile(regionId, parent / inputFile.name, inputFile)

        val pbStyles = Seq(ProgressBarStyle.success, ProgressBarStyle.striped, ProgressBarStyle.animated)
        val progressBar = div(
          div(b(inputFile.name)),
          ProgressBar.withLabel(progress).renderTag(pbStyles:_*),
          hr
        ).render
        progressBars.appendChild(progressBar)
        future.onComplete(_ ⇒ progressBars.removeChild(progressBar))

        future.foreach { file ⇒
          folderContext.update(parent)
          // dom.window.alert(file.toString)
          input.form.reset()
        }
      }
    })

    val scopeSelector = IndexScopeSelector.forContext(folderContext)
    val folderTree = FolderTree(Path.root)
    val folderView = FolderFileList(selectedFolderRx.map(_.files))

    div(
      GridSystem.row(
        GridSystem.col(6).asDiv(
          GridSystem.row(Form(uploadInput)),
          GridSystem.row(progressBars)
        ),
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

