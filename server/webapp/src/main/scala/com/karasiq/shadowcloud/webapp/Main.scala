package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportAll

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import org.scalajs.jquery._

import com.karasiq.shadowcloud.index.{Folder, Path}
import com.karasiq.shadowcloud.webapp.api.AjaxApi
import com.karasiq.shadowcloud.webapp.components.FolderView
import com.karasiq.shadowcloud.webapp.utils.RxWithUpdate

@JSExportAll
object Main extends JSApp {
  def main(): Unit = {
    jQuery(() ⇒ {
      val testRegion = "testRegion"
      val rootFolder = RxWithUpdate(Folder(Path.root))(_ ⇒ AjaxApi.getFolder(testRegion, Path.root))

      val input = FormInput.file("File", onchange := Callback.onInput { input ⇒
        val file = input.files.head
        AjaxApi.uploadFile(testRegion, Path.root / file.name, file).foreach { file ⇒
          rootFolder.update()
          dom.window.alert(file.toString)
        }
      })

      val container = GridSystem.container(
        GridSystem.mkRow(input),
        GridSystem.mkRow(FolderView(testRegion, rootFolder.toRx))
      )
      container.applyTo(dom.document.body)
    })
  }
}
