package com.karasiq.shadowcloud.webapp

import com.karasiq.bootstrap.Bootstrap
import com.karasiq.bootstrap.BootstrapImplicits._
import com.karasiq.bootstrap.form.FormInput
import com.karasiq.bootstrap.grid.GridSystem
import org.scalajs.dom
import org.scalajs.dom.ext.Ajax
import org.scalajs.jquery._

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSApp
import scala.scalajs.js.annotation.JSExportAll
import scalatags.JsDom.all._

@JSExportAll
object Main extends JSApp {
  def main(): Unit = {
    jQuery(() ⇒ {
      val input = FormInput.file("File", onchange := Bootstrap.jsInput { input ⇒
        val file = input.files.head
        Ajax.post("/test.txt", file).map(_.responseText).foreach(dom.window.alert)
      })

      val button = Bootstrap.button("Read", onclick := Bootstrap.jsClick { _ ⇒
        Ajax.get("/file/test.txt").map(_.responseText).foreach(dom.window.alert)
      })

      val container = GridSystem.container(
        GridSystem.mkRow(input),
        GridSystem.mkRow(button)
      )
      container.applyTo(dom.document.body)
    })
  }
}
