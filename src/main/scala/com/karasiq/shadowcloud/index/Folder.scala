package com.karasiq.shadowcloud.index

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

case class Folder(path: Path, folders: Set[String] = Set.empty, files: Set[File] = Set.empty) {
  require(files.forall(_.path == this.path))

  def addFiles(files: GenTraversableOnce[File]): Folder = {
    copy(files = this.files ++ files)
  }

  def addFolders(folders: GenTraversableOnce[String]): Folder = {
    copy(folders = this.folders ++ folders)
  }

  def addFiles(files: File*): Folder = {
    addFiles(files)
  }

  def addFolders(folders: String*): Folder = {
    addFolders(folders)
  }

  def deleteFolders(folders: GenTraversableOnce[String]): Folder = {
    copy(folders = this.folders -- folders)
  }

  def deleteFiles(files: GenTraversableOnce[File]): Folder = {
    copy(files = this.files -- files)
  }

  def deleteFolders(folders: String*): Folder = {
    deleteFolders(folders)
  }

  def deleteFiles(files: File*): Folder = {
    deleteFiles(files)
  }

  def merge(folder: Folder) = {
    require(path == folder.path, "Invalid path")
    addFolders(folder.folders).addFiles(folder.files)
  }

  def diff(folder: Folder) = {
    require(path == folder.path, "Invalid path")
    FolderDiff(this, folder)
  }

  def patch(diff: FolderDiff) = {
    this
      .deleteFiles(diff.deletedFiles)
      .addFiles(diff.newFiles)
      .deleteFolders(diff.deletedFolders)
      .addFolders(diff.newFolders)
  }

  override def toString = {
    s"Folder($path, folders: [${folders.mkString(", ")}], files: [${files.mkString(", ")}])"
  }
}