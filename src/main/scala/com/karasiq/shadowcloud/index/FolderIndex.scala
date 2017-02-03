package com.karasiq.shadowcloud.index

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

case class FolderIndex(folders: Map[Path, Folder] = Map(Path.root → Folder(Path.root))) {
  require(folders.contains(Path.root), "No root directory")

  def contains(folder: Path) = {
    folders.contains(folder)
  }

  def addFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val modified = files.toVector.groupBy(_.path).map { case (path, files) ⇒
      path → folders.getOrElse(path, Folder(path)).addFiles(files)
    }
    copy(folders ++ modified)
  }

  def addFolders(folders: GenTraversableOnce[Folder]): FolderIndex = {
    val newFolders = folders.toIterator.map { folder ⇒
      val existing = this.folders.get(folder.path)
      folder.path → existing.fold(folder)(_.merge(folder))
    }
    FolderIndex(newFolders.toMap)
  }

  def addFolders(folders: Folder*): FolderIndex = {
    addFolders(folders)
  }

  def addFiles(files: File*): FolderIndex = {
    addFiles(files)
  }

  def deleteFiles(files: GenTraversableOnce[File]): FolderIndex = {
    val newFolders = for {
      (path, folderFiles) ← files.toList.groupBy(_.path)
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
    val diffs = for {
      (path, folder) ← folders
      secondary ← second.folders.get(path)
      diff = folder.diff(secondary) if diff.nonEmpty
    } yield diff
    diffs.toVector
  }

  def patch(diffs: GenTraversableOnce[FolderDiff]): FolderIndex = {
    val newFolders = diffs.toIterator.flatMap { diff ⇒
      val folder = folders.get(diff.path)
      if (folder.isEmpty) {
        if (diff.newFiles.nonEmpty || diff.newFolders.nonEmpty)
          Some(Folder(diff.path).patch(diff))
        else
          None
      } else {
        folder.map(_.patch(diff))
      }
    }
    copy(folders ++ newFolders.map(f ⇒ (f.path, f)))
  }

  override def toString = {
    s"FolderIndex(${folders.values.mkString(", ")})"
  }
}

object FolderIndex {
  val empty = FolderIndex()

  def apply(folders: GenTraversableOnce[Folder]): FolderIndex = {
    empty.addFolders(folders)
  }
}