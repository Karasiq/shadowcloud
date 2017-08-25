package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom.raw.DOMParser

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext

object TextView {
  def apply(text: Metadata.Text)(implicit context: AppContext): TextView = {
    new TextView(text)
  }
}

class TextView(text: Metadata.Text)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    if (text.format == "text/html") {
      val domParser = new DOMParser
      val document = domParser.parseFromString(text.data, text.format)
      val documentBody = document.getElementsByTagName("body").headOption
      val htmlContent: Frag = documentBody
        .map(body ⇒ div(body.childNodes.map(node ⇒ node: Frag):_*))
        .getOrElse(document)
      Bootstrap.well(htmlContent, md)
    } else {
      pre(text.data, md)
    }
  }
}

