package com.karasiq.shadowcloud.index

import scala.language.{implicitConversions, postfixOps}

case class Path(nodes: Seq[String]) {
  def isRoot: Boolean = {
    nodes.isEmpty
  }

  def /(node: String): Path = {
    // require(node.nonEmpty, "Node is empty")
    copy(nodes :+ node)
  }

  def /(relPath: Path): Path = {
    copy(nodes ++ relPath.nodes)
  }

  def toRelative(root: Path): Path = {
    if (startsWith(root)) copy(nodes.drop(root.nodes.length)) else this
  }

  def startsWith(path: Path): Boolean = {
    nodes.startsWith(path.nodes)
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

  // Supports only conventional paths
  implicit def fromString(str: String): Path = {
    val nodes: Seq[String] = str.split("/").filter(_.nonEmpty)
    if (nodes.isEmpty) root else Path(nodes)
  }
}