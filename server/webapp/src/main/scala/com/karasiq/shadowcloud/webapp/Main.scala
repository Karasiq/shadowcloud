package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportAll

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import org.scalajs.jquery._
import rx.async._

import com.karasiq.shadowcloud.index.{Folder, Path}
import com.karasiq.shadowcloud.webapp.api.{AutowireApi, FileApi}
import com.karasiq.shadowcloud.webapp.components.FolderView
import com.karasiq.shadowcloud.webapp.utils.Blobs

@JSExportAll
object Main extends JSApp {
  def main(): Unit = {
    jQuery(() ⇒ {
      val testRegion = "testRegion"
      val testFilePath: Path = "test.txt"

      val rootFolder = AutowireApi.getFolder(testRegion, Path.root)
      val folderView = rootFolder.toRx(Folder(Path.root)).map(FolderView(_))
      
      val input = FormInput.file("File", onchange := Callback.onInput { input ⇒
        val file = input.files.head
        FileApi.uploadFile(testRegion, testFilePath, file).foreach(file ⇒ dom.window.alert(file.toString))
      })

      val button = Bootstrap.button("Read", onclick := Callback.onClick { _ ⇒
        FileApi.downloadFile(testRegion, testFilePath).flatMap(Blobs.toString).foreach(dom.window.alert)
      })

      val container = GridSystem.container(
        GridSystem.mkRow(input),
        GridSystem.mkRow(button),
        GridSystem.mkRow(folderView)
      )
      container.applyTo(dom.document.body)
    })
  }
}
