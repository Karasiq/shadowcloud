package com.karasiq.shadowcloud.webapp.controllers

import com.karasiq.shadowcloud.model.{Folder, Path}
import com.karasiq.shadowcloud.webapp.utils.HasKeyUpdate

trait FolderController extends HasKeyUpdate[Path] {
  def addFolder(folder: Folder): Unit
  def deleteFolder(folder: Folder): Unit
}

object FolderController {
  def apply(onUpdate: Path ⇒ Unit, onAddFolder: Folder ⇒ Unit, onDeleteFolder: Folder ⇒ Unit): FolderController = {
    new FolderController {
      def update(path: Path): Unit = onUpdate(path)
      def addFolder(folder: Folder): Unit = onAddFolder(folder)
      def deleteFolder(folder: Folder): Unit = onDeleteFolder(folder)
    }
  }
}