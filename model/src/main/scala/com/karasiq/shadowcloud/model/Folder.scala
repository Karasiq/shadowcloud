package com.karasiq.shadowcloud.model

import com.karasiq.shadowcloud.index.diffs.FolderDiff
import com.karasiq.shadowcloud.index.utils._
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.GenTraversableOnce

@SerialVersionUID(0L)
final case class Folder(path: Path, timestamp: Timestamp = Timestamp.now, folders: Set[String] = Set.empty, files: Set[File] = Set.empty)
    extends SCEntity
    with HasPath
    with HasEmpty
    with HasWithoutData
    with HasWithoutChunks
    with HasWithoutKeys
    with Mergeable {

  type Repr     = Folder
  type DiffRepr = FolderDiff

  def addFiles(files: GenTraversableOnce[File]): Folder = {
    val newFiles = this.files ++ files
    assert(newFiles.forall(_.path.parent == this.path), "Invalid file paths")
    copy(timestamp = timestamp.modifiedNow, files = newFiles)
  }

  def addFolders(folders: GenTraversableOnce[String]): Folder = {
    copy(timestamp = timestamp.modifiedNow, folders = this.folders ++ folders)
  }

  def addFiles(files: File*): Folder = {
    addFiles(files)
  }

  def addFolders(folders: String*): Folder = {
    addFolders(folders)
  }

  def deleteFolders(folders: GenTraversableOnce[String]): Folder = {
    copy(timestamp = timestamp.modifiedNow, folders = this.folders -- folders)
  }

  def deleteFiles(files: GenTraversableOnce[File]): Folder = {
    copy(timestamp = timestamp.modifiedNow, files = this.files -- files)
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

  def diff(oldFolder: Folder): FolderDiff = {
    require(path == oldFolder.path, "Invalid path")
    FolderDiff(oldFolder, this)
  }

  def patch(diff: FolderDiff): Folder = {
    copy(path, timestamp.modified(diff.time), folders ++ diff.newFolders -- diff.deletedFolders, files ++ diff.newFiles -- diff.deletedFiles)
  }

  def withPath(newPath: Path): Folder = {
    copy(path = newPath, files = files.map(file ⇒ file.copy(path = file.path.withParent(newPath))))
  }

  def isEmpty: Boolean = {
    files.isEmpty && folders.isEmpty
  }

  def withoutData: Folder = {
    copy(files = files.map(_.withoutData))
  }

  def withoutChunks: Folder = {
    copy(files = files.map(_.withoutChunks))
  }

  def withoutKeys = {
    copy(files = files.map(_.withoutKeys))
  }

  override def hashCode(): Int = {
    path.hashCode()
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case f: Folder ⇒
      f.path == path && f.folders == folders && f.files == files

    case _ ⇒
      false
  }

  override def toString: String = {
    s"Folder($path, folders: [${Utils.printValues(folders)}], files: [${Utils.printValues(files)}])"
  }
}

object Folder {
  def create(path: Path): Folder = {
    Folder(path)
  }
}
