package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.index.diffs.{FolderDiff, FolderIndexDiff}
import com.karasiq.shadowcloud.index.utils.{HasEmpty, HasWithoutData, Mergeable}
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.{GenTraversableOnce, mutable}
import scala.language.postfixOps

case class FolderIndex(folders: Map[Path, Folder] = Map(Path.root → Folder(Path.root)))
  extends Mergeable with HasEmpty with HasWithoutData {

  type Repr = FolderIndex
  type DiffRepr = FolderIndexDiff
  require(folders.contains(Path.root), "No root directory")

  def contains(folder: Path): Boolean = {
    folders.contains(folder)
  }

  def get(folder: Path): Option[Folder] = {
    folders.get(folder)
  }

  def addFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val diffs = files.toVector.groupBy(_.path.parent).map { case (path, files) ⇒
      FolderDiff(path, files.map(_.lastModified).max, newFiles = files.toSet)
    }
    applyDiffs(diffs)
  }

  def addFolders(folders: GenTraversableOnce[Folder]): FolderIndex = {
    val diffs = folders.toIterator.flatMap { folder ⇒
      val existing = this.folders.get(folder.path)
      val diff = if (existing.isEmpty) FolderDiff.create(folder) else folder.diff(existing.get)
      val parent = this.folders.get(folder.path.parent)
      if (folder.path.isRoot || parent.exists(_.folders.contains(folder.path.name))) {
        Iterator.single(diff)
      } else {
        Iterator(FolderDiff(folder.path.parent, folder.lastModified, newFolders = Set(folder.path.name)), diff)
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
    val modified = mutable.AnyRefMap[Path, Folder]()
    val deleted = mutable.Set[Path]()
    diffs.foreach { diff ⇒
      val folder = folders.get(diff.path)
      if (folder.isEmpty) {
        if (diff.newFiles.nonEmpty || diff.newFolders.nonEmpty) {
          if (!diff.path.isRoot) { // Add reference in parent folder
          val parent = diff.path.parent
            modified += parent → modified.get(parent)
              .orElse(folders.get(parent))
              .fold(Folder(parent, diff.time, diff.time, Set(diff.path.name)))(_.addFolders(diff.path.name))
          }
          modified += diff.path → Folder(diff.path).patch(diff)
        }
      } else {
        folder.map(_.patch(diff)).foreach(modified += diff.path → _)
      }

      diff.newFolders
        .map(diff.path / _)
        .filterNot(path ⇒ folders.contains(path) || modified.contains(path))
        .foreach(path ⇒ modified += path → Folder(path, diff.time, diff.time))
      diff.deletedFolders.map(diff.path / _).foreach(deleted +=)
    }
    withFolders(folders ++ modified -- deleted)
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
}