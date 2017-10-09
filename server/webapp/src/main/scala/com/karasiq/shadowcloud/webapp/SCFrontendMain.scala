package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps

import org.scalajs.dom
import org.scalajs.jquery._

import com.karasiq.shadowcloud.webapp.components.SCFrontend
import com.karasiq.shadowcloud.webapp.context.AppContext

object SCFrontendMain {
  def main(args: Array[String]): Unit = {
    jQuery(() ⇒ {
      // Context
      implicit val appContext = AppContext()

      // Styles
      AppContext.applyStyles()

      // Frontend
      val frontend = SCFrontend()
      frontend.applyTo(dom.document.body)
    })
  }
}
