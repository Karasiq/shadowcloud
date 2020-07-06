package com.karasiq.shadowcloud.model

@SerialVersionUID(0L)
final case class Path(nodes: Seq[String]) extends SCEntity {
  @transient
  private[this] lazy val _hashCode = scala.util.hashing.MurmurHash3.productHash(this)

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

  override def hashCode(): Int = {
    _hashCode
  }

  override def toString: String = {
    nodes.mkString(Path.Delimiter, Path.Delimiter, "")
  }
}

object Path {
  val Delimiter = "/"

  val root = Path(Vector.empty)

  // Supports only conventional paths
  implicit def fromString(str: String): Path = {
    val nodes = str.split(Delimiter).filter(_.nonEmpty)
    if (nodes.isEmpty) root else Path(nodes.toVector)
  }

  def isConventional(path: Path): Boolean = {
    path.nodes.forall(node ⇒ node.nonEmpty && !node.contains(Delimiter))
  }

  def isStrictlyConventional(path: Path): Boolean = {
    val forbiddenChars = """[<>:"/\\|?*]""".r
    path.nodes.forall(node ⇒ node.nonEmpty && forbiddenChars.findFirstIn(node).isEmpty)
  }

  def equalsIgnoreCase(path1: Path, path2: Path): Boolean = {
    def toLowerCase(path: Path) = path.copy(path.nodes.map(_.toLowerCase))
    toLowerCase(path1) == toLowerCase(path2)
  }

  implicit val ordering: Ordering[Path] = Ordering.by(path ⇒ (path.nodes.length, path.toString))
}
