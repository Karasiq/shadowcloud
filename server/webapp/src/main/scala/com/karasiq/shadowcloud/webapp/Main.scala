package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportAll

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import org.scalajs.dom.raw.HTMLStyleElement
import org.scalajs.jquery._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.file.FileView
import com.karasiq.shadowcloud.webapp.components.folder.{FolderFileList, FolderTree}
import com.karasiq.shadowcloud.webapp.components.region.IndexScopeSelector
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxUtils

@JSExportAll
object Main extends JSApp {
  def main(): Unit = {
    jQuery(() ⇒ {
      implicit val appContext = AppContext()

      // Styles
      appContext.styles.foreach { stylesheet ⇒
        import AppContext.CssSettings._
        dom.document.head.appendChild(stylesheet.render[HTMLStyleElement])
      }

      // Content
      val testRegion = "testRegion"
      val testFolder = Path.root / "TestFolder" / "TestSubFolder" / "TestSubSubFolder" / "/* TestSubSubSubFolder */"
      appContext.api.createFolder(testRegion, testFolder).foreach(println)

      implicit val folderContext = FolderContext(testRegion)
      folderContext.selected() = testFolder

      val selectedFolderRx = RxUtils.getSelectedFolderRx
      val uploadForm = FormInput.file("File", onchange := Callback.onInput { input ⇒
        val inputFile = input.files.head
        val parent = selectedFolderRx.now.path
        appContext.api.uploadFile(testRegion, parent / inputFile.name, inputFile).foreach { file ⇒
          folderContext.update(parent)
          dom.window.alert(file.toString)
        }
      })

      val scopeSelector = IndexScopeSelector.forContext(folderContext)
      val folderTree = FolderTree(Path.root)
      val folderView = FolderFileList(selectedFolderRx)

      val container = GridSystem.containerFluid(
        GridSystem.row(
          GridSystem.col(6).asDiv(uploadForm),
          GridSystem.col(6).asDiv(scopeSelector)
        ),
        GridSystem.row(
          GridSystem.col(3).asDiv(folderTree),
          GridSystem.col(6).asDiv(folderView),
          GridSystem.col(3).asDiv(folderView.selectedFile.map[Frag] {
            case Some(file) ⇒ FileView(file)
            case None ⇒ ()
          })
        )
      )

      container.applyTo(dom.document.body)
    })
  }
}
