package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.MergeUtil.State._
import com.karasiq.shadowcloud.utils.{MergeUtil, Utils}

import scala.collection.{GenTraversableOnce, mutable}
import scala.language.postfixOps

case class FolderIndex(folders: Map[Path, Folder] = Map(Path.root → Folder(Path.root, 0, 0))) {
  require(folders.contains(Path.root), "No root directory")

  def contains(folder: Path): Boolean = {
    folders.contains(folder)
  }

  def addFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val diffs = files.toVector.groupBy(_.parent).map { case (path, files) ⇒
      FolderDiff(path, files.map(_.lastModified).max, newFiles = files.toSet)
    }
    patch(diffs)
  }

  def addFolders(folders: GenTraversableOnce[Folder]): FolderIndex = {
    val diffs = folders.toIterator.flatMap { folder ⇒
      val existing = this.folders.get(folder.path)
      val diff = if (existing.isEmpty) FolderDiff.wrap(folder) else folder.diff(existing.get)
      val parent = this.folders.get(folder.path.parent)
      if (folder.path.isRoot || parent.exists(_.folders.contains(folder.path.name))) {
        Iterator.single(diff)
      } else {
        Iterator(FolderDiff(folder.path.parent, folder.lastModified, newFolders = Set(folder.path.name)), diff)
      }
    }
    patch(diffs)
  }

  def addFolders(folders: Folder*): FolderIndex = {
    addFolders(folders)
  }

  def addFiles(files: File*): FolderIndex = {
    addFiles(files)
  }

  def deleteFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val diffs = files.toVector.groupBy(_.parent).map { case (path, files) ⇒
      FolderDiff(path, Utils.timestamp, deletedFiles = files.toSet)
    }
    patch(diffs)
  }

  def deleteFolders(folders: GenTraversableOnce[Path]): FolderIndex = {
    val deleted = folders.toVector.groupBy(_.parent).map { case (path, folders) ⇒
      FolderDiff(path, Utils.timestamp, deletedFolders = folders.map(_.name).toSet)
    }
    patch(deleted)
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

  def diff(second: FolderIndex): Seq[FolderDiff] = {
    val diffs = MergeUtil.compareMaps(this.folders, second.folders).values.flatMap {
      case Left(folder) ⇒
        Iterator.single(FolderDiff.wrap(folder))

      case Right(folder) ⇒
        Iterator.single(FolderDiff.wrap(folder))

      case Conflict(left, right) ⇒
        Iterator.single(right.diff(left))

      case Equal(_) ⇒
        Iterator.empty
    }
    diffs.toVector
  }

  def patch(diffs: GenTraversableOnce[FolderDiff]): FolderIndex = {
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
    copy(folders ++ modified -- deleted)
  }

  override def toString: String = {
    s"FolderIndex(${folders.values.mkString(", ")})"
  }
}

object FolderIndex {
  val empty = FolderIndex()

  def apply(folders: GenTraversableOnce[Folder]): FolderIndex = {
    empty.addFolders(folders)
  }
}