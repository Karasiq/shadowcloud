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
    // if (isRoot) "/" else nodes.last
    require(!isRoot, "Root path has no name")
    nodes.last
  }

  def withParent(path: Path): Path = {
    if (isRoot) path else path / name
  }

  def withName(name: String): Path = {
    if (isRoot) this / name else parent / name
  }

  override def toString: String = {
    nodes.mkString("/", "/", "")
  }
}

object Path {
  val root = Path(Nil)

  implicit def fromString(str: String): Path = {
    val nodes: Seq[String] = str.split("/").filter(_.nonEmpty)
    if (nodes.isEmpty) root else Path(nodes)
  }
}