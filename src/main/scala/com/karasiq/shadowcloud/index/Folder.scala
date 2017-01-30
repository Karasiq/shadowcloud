package com.karasiq.shadowcloud.index

import scala.language.postfixOps

case class Folder(path: Path, folders: Set[String], files: Set[File]) {
  def merge(folder: Folder) = {
    require(path == folder.path, "Invalid path")
    copy(folders = folders ++ folder.folders, files = files ++ folder.files)
  }

  def +(file: File) = {
    copy(files = files + file)
  }

  def +(folder: String) = {
    copy(folders = folders + folder)
  }

  override def toString = {
    s"Folder($path, folders: [${folders.mkString(", ")}], files: [${files.mkString(", ")}])"
  }
}