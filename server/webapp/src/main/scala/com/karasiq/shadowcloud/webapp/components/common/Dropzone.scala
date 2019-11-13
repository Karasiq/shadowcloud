package com.karasiq.shadowcloud.webapp.components.common

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.api.{SCApiEncoding, SCApiUtils}
import com.karasiq.shadowcloud.model.{Path, RegionId}
import com.karasiq.shadowcloud.webapp.api.AjaxApi
import com.karasiq.shadowcloud.webapp.utils.URLPath
import org.scalajs.dom
import scalaTags._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("Dropzone")
class Dropzone(element: js.Any, options: js.Object) extends js.Object {
  def on[T <: js.Any](event: String, f: js.Function1[T, Unit]): Unit = js.native
  def removeAllFiles(): Unit                                         = js.native
}

object Dropzone {
  def apply(regionId: RegionId, path: Path, onSuccess: dom.File => Unit): Modifier = { element: dom.Element =>
    val url = URLPath(Path.root / "upload_form" / regionId / SCApiEncoding.toUrlSafe(AjaxApi.encoding.encodePath(path))).toString
    val dz = new Dropzone(
      element,
      js.Dynamic.literal(url = url.toString, headers = js.Dynamic.literal("X-Requested-With" -> SCApiUtils.RequestedWith, "Accept" -> AjaxApi.payloadContentType)).asInstanceOf[js.Object]
    )
    dz.on("success", onSuccess _)
  }

  def dynamic(process: dom.File => Unit): Modifier = { element: dom.Element =>
    val dropzone = new Dropzone(element, js.Dynamic.literal(url = "/", autoProcessQueue = false).asInstanceOf[js.Object])
    dropzone.on("addedfile", { f: dom.File =>
      process(f)
      dropzone.removeAllFiles()
    })
  }
}
