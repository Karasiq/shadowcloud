package com.karasiq.shadowcloud.index

import scala.language.{implicitConversions, postfixOps}

case class Path(nodes: Seq[String]) {
  def isRoot: Boolean = {
    nodes.isEmpty
  }

  def /(node: String): Path = {
    if (node.nonEmpty) copy(nodes :+ node) else this
  }

  def parent: Path = {
    if (nodes.length <= 1) Path.root else copy(nodes.dropRight(1))
  }

  def name: String = {
    if (isRoot) "/" else nodes.last
  }

  override def toString: String = {
    nodes.mkString("/", "/", "")
  }
}

object Path {
  val root = Path(Nil)

  implicit def fromString(str: String): Path = {
    val nodes: Seq[String] = str.split(separators).filter(_.nonEmpty)
    if (nodes.nonEmpty) Path(nodes) else root
  }

  private[this] val separators = Array('/', '\\')
}

trait HasPath {
  def path: Path
}