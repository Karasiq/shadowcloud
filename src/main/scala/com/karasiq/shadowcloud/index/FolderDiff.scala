package com.karasiq.shadowcloud.index

import scala.language.postfixOps

case class FolderDiff(path: Path, newFiles: Set[File], deletedFiles: Set[File], newFolders: Set[String], deletedFolders: Set[String]) {
  def nonEmpty: Boolean = {
    newFiles.nonEmpty || deletedFiles.nonEmpty || newFolders.nonEmpty || deletedFolders.nonEmpty
  }
}

object FolderDiff {
  def apply(folder: Folder, secondFolder: Folder): FolderDiff = {
    require(folder.path == secondFolder.path, "Invalid path")
    FolderDiff(
      folder.path,
      secondFolder.files.diff(folder.files),
      folder.files.diff(secondFolder.files),
      secondFolder.folders.diff(folder.folders),
      folder.folders.diff(secondFolder.folders)
    )
  }
}