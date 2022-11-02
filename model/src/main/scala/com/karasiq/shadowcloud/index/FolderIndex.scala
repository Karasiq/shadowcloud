package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.index.diffs.{FolderDiff, FolderIndexDiff}
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData, Mergeable}
import com.karasiq.shadowcloud.model.{File, Folder, Path, SCEntity}
import com.karasiq.shadowcloud.utils.Utils

import scala.annotation.tailrec
import scala.collection.{GenTraversableOnce, mutable}

@SerialVersionUID(0L)
final case class FolderIndex(folders: Map[Path, Folder] = Map(Path.root → Folder(Path.root)))
    extends SCEntity
    with Mergeable
    with HasEmpty
    with HasWithoutData {

  type Repr     = FolderIndex
  type DiffRepr = FolderIndexDiff
  require(folders.contains(Path.root), "No root directory")

  def contains(folder: Path): Boolean = {
    folders.contains(folder)
  }

  def get(folder: Path): Option[Folder] = {
    folders.get(folder)
  }

  def filesIterator: Iterator[File] = {
    folders.valuesIterator.flatMap(_.files.iterator)
  }

  def getFiles(path: Path): Set[File] = {
    // require(!path.isRoot, "Invalid file path")
    Some(path)
      .filterNot(_.isRoot)
      .flatMap(path ⇒ get(path.parent))
      .iterator
      .flatMap(_.files.iterator.filter(_.path == path))
      .toSet
  }

  def addFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val diffs = files.toVector.groupBy(_.path.parent).map { case (path, files) ⇒
      FolderDiff(path, files.map(_.timestamp.lastModified).max, newFiles = files.toSet)
    }
    applyDiffs(diffs)
  }

  def addFolders(folders: GenTraversableOnce[Folder]): FolderIndex = {
    val diffs = folders.toIterator.flatMap { folder ⇒
      val existing = this.folders.get(folder.path)
      val diff     = if (existing.isEmpty) FolderDiff.create(folder) else folder.diff(existing.get)
      val parent   = this.folders.get(folder.path.parent)
      if (folder.path.isRoot || parent.exists(_.folders.contains(folder.path.name))) {
        Iterator.single(diff)
      } else {
        Iterator(FolderDiff(folder.path.parent, folder.timestamp.lastModified, newFolders = Set(folder.path.name)), diff)
      }
    }
    applyDiffs(diffs)
  }

  def addFolders(folders: Folder*): FolderIndex = {
    addFolders(folders)
  }

  def addFiles(files: File*): FolderIndex = {
    addFiles(files)
  }

  def deleteFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val diffs = files.toVector.groupBy(_.path.parent).map { case (path, files) ⇒
      FolderDiff(path, Utils.timestamp, deletedFiles = files.toSet)
    }
    applyDiffs(diffs)
  }

  def deleteFolders(folders: GenTraversableOnce[Path]): FolderIndex = {
    val deleted = folders.toVector.groupBy(_.parent).map { case (path, folders) ⇒
      FolderDiff(path, Utils.timestamp, deletedFolders = folders.map(_.name).toSet)
    }
    applyDiffs(deleted)
  }

  def deleteFiles(files: File*): FolderIndex = {
    deleteFiles(files)
  }

  def deleteFolders(folders: Path*): FolderIndex = {
    deleteFolders(folders)
  }

  def merge(second: FolderIndex): FolderIndex = {
    addFolders(second.folders.values)
  }

  def diff(second: FolderIndex): FolderIndexDiff = {
    FolderIndexDiff(this, second)
  }

  def patch(diff: FolderIndexDiff): FolderIndex = {
    applyDiffs(diff.folders)
  }

  def isEmpty: Boolean = {
    folders == Map(Path.root → Folder(Path.root))
  }

  def withoutData: FolderIndex = {
    withFolders(folders.mapValues(_.withoutData))
  }

  override def toString: String = {
    s"FolderIndex(${folders.values.mkString(", ")})"
  }

  private[this] def applyDiffs(diffs: GenTraversableOnce[FolderDiff]): FolderIndex = {
    if (diffs.isEmpty) return this

    val modified = mutable.AnyRefMap[Path, Folder]()
    val deleted  = mutable.Set[Path]()

    def getFolder(path: Path) = {
      modified
        .get(path)
        .orElse(this.get(path))
    }

    def getOrCreate(path: Path): Folder = {
      getFolder(path)
        .getOrElse(Folder.create(path))
    }

    def isDeleteOnly(diff: FolderDiff): Boolean = {
      diff.newFiles.isEmpty && diff.newFolders.isEmpty
    }

    @tailrec
    def createParentFolders(path: Path): Unit = {
      if (path.isRoot || deleted.contains(path)) return
      val parent = getOrCreate(path.parent)
      modified += parent.path → parent.addFolders(path.name)
      createParentFolders(parent.path)
    }

    @tailrec
    def deleteFolders(paths: Set[Path]): Unit = {
      if (paths.isEmpty) return
      deleted ++= paths

      val children: Set[Path] =
        for (path ← paths; folder ← getFolder(path).toSeq; subFolder ← folder.folders)
          yield path / subFolder

      deleteFolders(children)
    }

    val sortedDiffs = diffs.toVector.sortBy(_.path)(Ordering[Path].reverse)
    sortedDiffs.foreach { diff ⇒
      if (isDeleteOnly(diff)) {
        for (folder ← getFolder(diff.path))
          modified += diff.path → folder.patch(diff)
      } else {
        createParentFolders(diff.path)
        val folder = getOrCreate(diff.path)
        modified += diff.path → folder.patch(diff)
      }

      diff.newFolders
        .map(diff.path / _)
        .filterNot(path ⇒ folders.contains(path) || modified.contains(path))
        .foreach(path ⇒ modified += path → getOrCreate(path))

      deleteFolders(diff.deletedFolders.map(diff.path / _))
    }
    withFolders((this.folders ++ modified) -- deleted)
  }

  private[this] def withFolders(folders: Map[Path, Folder]): FolderIndex = {
    if (folders.isEmpty) FolderIndex.empty else copy(folders)
  }
}

object FolderIndex {
  val empty = FolderIndex()

  def apply(folders: GenTraversableOnce[Folder]): FolderIndex = {
    empty.addFolders(folders)
  }

  def traverseFolderTree(index: FolderIndex, path: Path): Iterator[Folder] = {
    def getSubFolders(index: FolderIndex, folder: Folder): Seq[Folder] = {
      folder.folders.toVector.map(folder.path / _).flatMap(index.get)
    }

    def traverseFolderTreeRec(index: FolderIndex, folder: Folder): Iterator[Folder] = {
      val subFolders = getSubFolders(index, folder)
      Iterator.single(folder) ++ subFolders.flatMap(traverseFolderTreeRec(index, _))
    }

    index
      .get(path)
      .iterator
      .flatMap(traverseFolderTreeRec(index, _))
  }
}
