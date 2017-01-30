package com.karasiq.shadowcloud.index

import scala.language.postfixOps

case class FolderIndex(folders: Map[Path, Folder] = Map(Path.root → Folder(Path.root, Set.empty, Set.empty))) {
  require(folders.contains(Path.root), "No root directory")

  def merge(second: FolderIndex) = {
    val newFolders = Map.newBuilder[Path, Folder]
    newFolders ++= folders
    second.folders.foreach {
      case (path, folder) ⇒
        folders.get(path) match {
          case Some(existing) ⇒
            newFolders += path → existing.merge(folder)

          case None ⇒
            newFolders += path → folder
        }
    }

    FolderIndex(newFolders.result())
  }

  def contains(folder: Path) = {
    folders.contains(folder)
  }

  def +(folder: Folder) = {
    val newFolders = folders + (folder.path → folders.get(folder.path).fold(folder)(_.merge(folder)))
    copy(newFolders)
  }

  def +(file: File) = {
    val newFolders = folders + (file.path → folders.get(file.path).fold(Folder(file.path, Set.empty, Set(file)))(_ + file))
    copy(newFolders)
  }

  override def toString = {
    s"FolderIndex(${folders.values.mkString(", ")})"
  }
}

object FolderIndex {
  val empty = FolderIndex()
}