package com.karasiq.shadowcloud.webapp

import com.karasiq.shadowcloud.webapp.components.SCFrontend
import com.karasiq.shadowcloud.webapp.context.AppContext
import org.scalajs.jquery._

object SCFrontendMain {
  def main(args: Array[String]): Unit = {
    jQuery(() ⇒ {
      // Context
      implicit val appContext = AppContext()

      // Styles
      AppContext.applyStyles()

      // Frontend
      val frontend = SCFrontend()
      frontend.init()
    })
  }
}
