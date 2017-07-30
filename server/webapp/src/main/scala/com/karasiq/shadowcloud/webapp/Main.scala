package com.karasiq.shadowcloud.webapp

import scala.concurrent.Future
import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.{JSApp, URIUtils}
import scala.scalajs.js.annotation.JSExportAll

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.dom.raw.Blob
import org.scalajs.jquery._
import play.api.libs.json.{Json, Reads}

import com.karasiq.shadowcloud.api.SCJsonEncoding
import com.karasiq.shadowcloud.index.{File, Path}
import com.karasiq.shadowcloud.webapp.utils.Blobs

@JSExportAll
object Main extends JSApp {
  object FileApi {
    import com.karasiq.shadowcloud.api.SCJsonEncoders._

    private[this] def decodeResponse[R: Reads](r: dom.XMLHttpRequest): R = {
      Json.parse(r.responseText).as[R]
    }

    def urlFromPath(path: Path ⇒ Path): String = {
      path(Path.root).nodes.map(URIUtils.encodeURIComponent).mkString("/")
    }

    def uploadFile(regionId: String, path: Path, data: Ajax.InputData): Future[File] = {
      Ajax.post(uploadFileUrl(regionId, path), data).map(decodeResponse[File])
    }

    def uploadFileUrl(regionId: String, path: Path): String = {
      urlFromPath(_ / "upload" / regionId) + "?path=" + URIUtils.encodeURIComponent(SCJsonEncoding.encodePath(path))
    }

    def downloadFile(regionId: String, path: Path): Future[Blob] = {
      Ajax.get(downloadFileUrl(regionId, path), responseType = "blob")
        .filter(_.status == 200)
        .map(_.response.asInstanceOf[Blob])
    }

    def downloadFileUrl(regionId: String, path: Path): String = {
      require(!path.isRoot, "Not a file")
      urlFromPath(_ / "download" / regionId / path.name) + "?path=" + URIUtils.encodeURIComponent(SCJsonEncoding.encodePath(path))
    }
  }

  def main(): Unit = {
    jQuery(() ⇒ {
      val testRegion = "testRegion"
      val testFilePath: Path = "test.txt"
      
      val input = FormInput.file("File", onchange := Callback.onInput { input ⇒
        val file = input.files.head
        FileApi.uploadFile(testRegion, testFilePath, file).foreach(file ⇒ dom.window.alert(file.toString))
      })

      val button = Bootstrap.button("Read", onclick := Callback.onClick { _ ⇒
        FileApi.downloadFile(testRegion, testFilePath).flatMap(Blobs.toString).foreach(dom.window.alert)
      })

      val container = GridSystem.container(
        GridSystem.mkRow(input),
        GridSystem.mkRow(button)
      )
      container.applyTo(dom.document.body)
    })
  }
}
