package com.karasiq.shadowcloud.index

import scala.language.{implicitConversions, postfixOps}

case class Path(nodes: Seq[String]) {
  def isRoot = {
    nodes.isEmpty
  }

  def /(node: String) = {
    if (node.nonEmpty) copy(nodes :+ node) else this
  }

  def parent = {
    if (nodes.nonEmpty) copy(nodes.dropRight(1)) else this
  }

  def name = {
    if (nodes.nonEmpty) nodes.last else "/"
  }

  override def toString = {
    nodes.mkString("/", "/", "")
  }
}

object Path {
  val root = Path(Nil)

  implicit def fromString(str: String): Path = {
    val nodes: Seq[String] = str.split(Array('/', '\\')).filter(_.nonEmpty)
    if (nodes.nonEmpty) Path(nodes) else root
  }
}