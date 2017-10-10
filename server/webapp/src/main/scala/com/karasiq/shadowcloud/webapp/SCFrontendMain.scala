package com.karasiq.shadowcloud.webapp

import scala.language.postfixOps

import org.scalajs.dom
import org.scalajs.jquery._

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.webapp.components.{SCContextBinding, SCFrontend}
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.utils.RxLocation

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

      val cb = SCContextBinding()
      cb.bindToFrontend(frontend)
      cb.bindToString(RxLocation().hash)
      cb.toTitleRx.foreach { title ⇒
        dom.document.title = "shadowcloud - " + title
      }
    })
  }
}
