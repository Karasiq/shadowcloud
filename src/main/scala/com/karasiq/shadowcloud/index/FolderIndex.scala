package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.MergeUtil
import com.karasiq.shadowcloud.utils.MergeUtil.State._

import scala.collection.{GenTraversableOnce, mutable}
import scala.language.postfixOps

case class FolderIndex(folders: Map[Path, Folder] = Map(Path.root → Folder(Path.root, 0, 0))) {
  require(folders.contains(Path.root), "No root directory")

  def contains(folder: Path): Boolean = {
    folders.contains(folder)
  }

  def addFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val modified = files.toTraversable.groupBy(_.parent).map { case (path, files) ⇒
      path → folders.getOrElse(path, Folder(path)).addFiles(files)
    }
    copy(folders ++ modified)
  }

  def addFolders(folders: GenTraversableOnce[Folder]): FolderIndex = {
    val newFolders = MergeUtil.mergeMaps[Path, Folder](this.folders, folders.toIterator.map(f ⇒ (f.path, f)).toMap, {
      case Conflict(left, right) ⇒
        Some(left.merge(right))
    })
    copy(newFolders)
  }

  def addFolders(folders: Folder*): FolderIndex = {
    addFolders(folders)
  }

  def addFiles(files: File*): FolderIndex = {
    addFiles(files)
  }

  def deleteFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val newFolders = for {
      (path, folderFiles) ← files.toList.groupBy(_.parent)
      folder ← folders.get(path)
    } yield (path, folder.deleteFiles(folderFiles))
    FolderIndex(folders ++ newFolders)
  }

  def deleteFolders(folders: GenTraversableOnce[Path]): FolderIndex = {
    val deleted = folders.toVector
    val newFolders = this.folders.filterKeys(path ⇒ !deleted.exists(df ⇒ path.nodes.startsWith(df.nodes)))
    copy(newFolders)
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
        Some(FolderDiff.wrap(folder))

      case Right(folder) ⇒
        Some(FolderDiff.wrap(folder))

      case Conflict(left, right) ⇒
        Some(left.diff(right))

      case Equal(_) ⇒
        None
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
          modified += diff.path → Folder(diff.path).patch(diff)
        }
      } else {
        folder.map(_.patch(diff)).foreach(modified += diff.path → _)
      }

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