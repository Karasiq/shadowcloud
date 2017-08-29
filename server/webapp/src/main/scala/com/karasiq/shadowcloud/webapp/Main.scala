package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportAll

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import org.scalajs.jquery._

import com.karasiq.shadowcloud.model.Folder
import com.karasiq.shadowcloud.webapp.api.AjaxApi
import com.karasiq.shadowcloud.webapp.components.file.FileView
import com.karasiq.shadowcloud.webapp.components.folder.FolderFileList
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.utils.RxWithUpdate

@JSExportAll
object Main extends JSApp {
  def main(): Unit = {
    jQuery(() ⇒ {
      implicit val appContext = AppContext()
      val testRegion = "testRegion"
      val rootFolder = RxWithUpdate(Folder(Path.root))(_ ⇒ AjaxApi.getFolder(testRegion, Path.root))

      val input = FormInput.file("File", onchange := Callback.onInput { input ⇒
        val file = input.files.head
        AjaxApi.uploadFile(testRegion, Path.root / file.name, file).foreach { file ⇒
          rootFolder.update()
          dom.window.alert(file.toString)
        }
      })

      val folderView = FolderFileList(testRegion, rootFolder.toRx)
      val container = GridSystem.container(
        GridSystem.mkRow(input),
        GridSystem.row(
          GridSystem.col(8).asDiv(folderView),
          GridSystem.col(4).asDiv(folderView.selectedFile.map[Frag] {
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
