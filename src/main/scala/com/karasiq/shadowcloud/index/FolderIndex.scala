package com.karasiq.shadowcloud.index

import scala.language.postfixOps

case class FolderIndex(folders: Map[Path, Folder] = Map(Path.root → Folder(Path.root, Set.empty, Set.empty))) {
  require(folders.contains(Path.root), "No root directory")

  def merge(second: FolderIndex): FolderIndex = {
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
}