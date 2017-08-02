package com.karasiq.shadowcloud.webapp.utils

import scala.scalajs.js.URIUtils

import play.api.libs.json.{Json, Writes}

import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.index.utils.HasEmpty

case class URLPath(path: Path = Path.root, query: Map[String, String] = Map.empty) extends HasEmpty {
  def isEmpty: Boolean = path.isRoot && query.isEmpty

  def withPath(newPath: Path ⇒ Path): URLPath = {
    copy(path = newPath(path))
  }

  def withQuery(name: String, value: String): URLPath = {
    copy(query = query + (name → value))
  }

  def withQueryJson[T: Writes](name: String, value: T): URLPath = {
    withQuery(name, Json.stringify(Json.toJson(value)))
  }

  override def toString: String = {
    if (isEmpty) return "/"
    val sb = new StringBuilder(100)
    path.nodes.foreach { node ⇒
      sb += '/'
      sb ++= URIUtils.encodeURIComponent(node)
    }

    for (((key, value), index) ← query.zipWithIndex) {
      if (index == 0) sb += '?' else sb += '&'
      sb ++= URIUtils.encodeURIComponent(key)
      sb += '='
      sb ++= URIUtils.encodeURIComponent(value)
    }

    sb.result()
  }
}

object URLPath {
  def apply(path: Path ⇒ Path): URLPath = {
    URLPath().withPath(path)
  }

  def fromString(urlString: String): URLPath = {
    urlString.split("\\?", 2) match {
      case Array(pathString, queryString) ⇒
        val queryParams = queryString
          .split("&")
          .map(_.split("=", 2))
          .collect { case Array(key, value) ⇒ (key, value) }
        URLPath(pathString: Path, queryParams.toMap)

      case Array(pathString) ⇒
        URLPath(pathString: Path)

      case _ ⇒
        URLPath()
    }
  }
}
