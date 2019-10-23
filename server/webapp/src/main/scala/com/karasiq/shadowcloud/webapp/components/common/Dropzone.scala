package com.karasiq.shadowcloud.webapp.components.common

import com.karasiq.bootstrap.Bootstrap.default._
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
  def apply(process: dom.File => Unit): Modifier = { e: dom.Element =>
    val dropzone = new Dropzone(e, js.Dynamic.literal(url = "/", autoProcessQueue = false).asInstanceOf[js.Object])
    dropzone.on("addedfile", { f: dom.File =>
      process(f)
      dropzone.removeAllFiles()
    })
  }
}
