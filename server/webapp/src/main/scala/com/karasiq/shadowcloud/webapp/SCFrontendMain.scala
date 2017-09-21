package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import org.scalajs.dom
import org.scalajs.jquery._

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.components.SCFrontend
import com.karasiq.shadowcloud.webapp.context.AppContext

object SCFrontendMain {
  def main(args: Array[String]): Unit = {
    jQuery(() ⇒ {
      // Context
      implicit val appContext = AppContext()

      // Styles
      AppContext.applyStyles()

      // Content
      val testRegion = "testRegion"
      val testFolder = Path.root / "TestFolder" / "TestSubFolder" / "TestSubSubFolder" / "/* TestSubSubSubFolder */"
      appContext.api.createFolder(testRegion, testFolder).foreach(println)

      val frontend = SCFrontend()
      frontend.applyTo(dom.document.body)
    })
  }
}
