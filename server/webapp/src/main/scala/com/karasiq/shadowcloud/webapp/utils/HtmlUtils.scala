package com.karasiq.shadowcloud.webapp.utils

import org.scalajs
import org.scalajs.dom.raw.DOMParser

object HtmlUtils {
  val HtmlMime = "text/html"

  def extractContent(html: String, format: String = HtmlMime): Seq[scalajs.dom.Node] = {
    import com.karasiq.bootstrap.Bootstrap.default.DOMListIndexedSeq

    val domParser = new DOMParser
    val document = domParser.parseFromString(html, format)
    val documentBody = document.getElementsByTagName("body").headOption
    documentBody
      .map(body ⇒ body.childNodes.map(node ⇒ node))
      .getOrElse(Seq(document))
  }
}
