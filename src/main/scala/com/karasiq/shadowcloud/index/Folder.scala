package com.karasiq.shadowcloud.index

import com.karasiq.shadowcloud.utils.Utils

import scala.collection.GenTraversableOnce
import scala.language.postfixOps

case class Folder(path: Path, created: Long = 0, lastModified: Long = 0, folders: Set[String] = Set.empty, files: Set[File] = Set.empty) {
  require(files.forall(_.parent == this.path))

  def addFiles(files: GenTraversableOnce[File]): Folder = {
    val newFiles = this.files ++ files
    copy(lastModified = Utils.timestamp, files = newFiles)
  }

  def addFolders(folders: GenTraversableOnce[String]): Folder = {
    copy(lastModified = Utils.timestamp, folders = this.folders ++ folders)
  }

  def addFiles(files: File*): Folder = {
    addFiles(files)
  }

  def addFolders(folders: String*): Folder = {
    addFolders(folders)
  }

  def deleteFolders(folders: GenTraversableOnce[String]): Folder = {
    copy(lastModified = Utils.timestamp, folders = this.folders -- folders)
  }

  def deleteFiles(files: GenTraversableOnce[File]): Folder = {
    copy(lastModified = Utils.timestamp, files = this.files -- files)
  }

  def deleteFolders(folders: String*): Folder = {
    deleteFolders(folders)
  }

  def deleteFiles(files: File*): Folder = {
    deleteFiles(files)
  }

  def merge(folder: Folder): Folder = {
    require(path == folder.path, "Invalid path")
    addFolders(folder.folders).addFiles(folder.files)
  }

  def diff(folder: Folder): FolderDiff = {
    require(path == folder.path, "Invalid path")
    FolderDiff(this, folder)
  }

  def patch(diff: FolderDiff): Folder = {
    this
      .deleteFiles(diff.deletedFiles)
      .addFiles(diff.newFiles)
      .deleteFolders(diff.deletedFolders)
      .addFolders(diff.newFolders)
      .copy(lastModified = math.max(lastModified, diff.time))
  }

  override def toString: String = {
    s"Folder($path, folders: [${folders.mkString(", ")}], files: [${files.mkString(", ")}])"
  }
}