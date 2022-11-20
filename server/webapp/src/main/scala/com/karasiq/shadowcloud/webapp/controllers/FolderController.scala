package com.karasiq.shadowcloud.webapp.controllers

import com.karasiq.shadowcloud.model.{Folder, Path}
import com.karasiq.shadowcloud.webapp.context.FolderContext
import com.karasiq.shadowcloud.webapp.utils.HasKeyUpdate

trait FolderController extends HasKeyUpdate[Path] {
  def addFolder(folder: Folder): Unit
  def deleteFolder(folder: Folder): Unit
}

object FolderController {
  def apply(onUpdate: Path ⇒ Unit, onAddFolder: Folder ⇒ Unit, onDeleteFolder: Folder ⇒ Unit): FolderController = {
    new FolderController {
      def update(path: Path): Unit           = onUpdate(path)
      def addFolder(folder: Folder): Unit    = onAddFolder(folder)
      def deleteFolder(folder: Folder): Unit = onDeleteFolder(folder)
    }
  }

  def inherit(onUpdate: Path ⇒ Unit = _ ⇒ (), onAddFolder: Folder ⇒ Unit = _ ⇒ (), onDeleteFolder: Folder ⇒ Unit = _ ⇒ ())(implicit
      fc: FolderController
  ): FolderController = apply(
    path ⇒ { fc.update(path); onUpdate(path) },
    folder ⇒ { fc.addFolder(folder); onAddFolder(folder) },
    folder ⇒ { fc.deleteFolder(folder); onDeleteFolder(folder) }
  )

  def forFolderContext(implicit folderContext: FolderContext): FolderController = apply(
    path ⇒ folderContext.update(path),
    folder ⇒ {
      folderContext.update(folder.path.parent)
      folderContext.update(folder.path)
    },
    folder ⇒ folderContext.update(folder.path.parent)
  )
}
