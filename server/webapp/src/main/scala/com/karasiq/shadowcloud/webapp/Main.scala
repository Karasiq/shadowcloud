package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportAll

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import org.scalajs.jquery._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.file.FileView
import com.karasiq.shadowcloud.webapp.components.folder.{FolderFileList, FolderTree}
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxUtils

@JSExportAll
object Main extends JSApp {
  def main(): Unit = {
    jQuery(() ⇒ {
      implicit val appContext = AppContext()
      val testRegion = "testRegion"
      appContext.api.createFolder(testRegion, Path.root / "TestFolder").foreach(println)

      implicit val folderContext = FolderContext(testRegion)
      val selectedFolderRx = RxUtils.toSelectedFolderRx(folderContext)

      val input = FormInput.file("File", onchange := Callback.onInput { input ⇒
        val inputFile = input.files.head
        appContext.api.uploadFile(testRegion, selectedFolderRx.key.now / inputFile.name, inputFile).foreach { file ⇒
          selectedFolderRx.update()
          dom.window.alert(file.toString)
        }
      })

      val folderTree = FolderTree(testRegion, Path.root)
      val folderView = FolderFileList(testRegion, selectedFolderRx.toRx)

      val container = GridSystem.containerFluid(
        GridSystem.mkRow(input),
        GridSystem.row(
          GridSystem.col(2).asDiv(folderTree),
          GridSystem.col(7).asDiv(folderView),
          GridSystem.col(3).asDiv(folderView.selectedFile.map[Frag] {
            case Some(file) ⇒
              FileView(testRegion, file)

            case None ⇒
              ()
          })
        )
      )
      container.applyTo(dom.document.body)
    })
  }
}
