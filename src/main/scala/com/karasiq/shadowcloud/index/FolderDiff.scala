package com.karasiq.shadowcloud.index

import scala.language.postfixOps

case class FolderDiff(path: Path, newFiles: Set[File] = Set.empty, deletedFiles: Set[File] = Set.empty, newFolders: Set[String] = Set.empty, deletedFolders: Set[String] = Set.empty) {
  def nonEmpty: Boolean = {
    newFiles.nonEmpty || deletedFiles.nonEmpty || newFolders.nonEmpty || deletedFolders.nonEmpty
  }

  def merge(diff: FolderDiff): FolderDiff = {
    require(diff.path == path, "Invalid path")
    copy(path, newFiles -- diff.deletedFiles ++ diff.newFiles, deletedFiles -- diff.newFiles ++ diff.deletedFiles, newFolders -- diff.deletedFolders ++ diff.newFolders, deletedFolders -- diff.newFolders ++ diff.deletedFolders)
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