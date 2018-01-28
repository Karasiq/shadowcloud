package com.karasiq.shadowcloud.webapp.controllers

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.context.FolderContext

trait FileController {
  def addFile(file: File): Unit
  def deleteFile(file: File): Unit
  def updateFile(oldFile: File, newFile: File): Unit
  def renameFile(file: File, newName: String): Unit
}

object FileController {
  def apply(onAddFile: File ⇒ Unit,
            onDeleteFile: File ⇒ Unit,
            onUpdateFile: (File, File) ⇒ Unit,
            onRenameFile: (File, String) ⇒ Unit): FileController = {
    new FileController {
      def addFile(file: File): Unit = onAddFile(file)
      def deleteFile(file: File): Unit = onDeleteFile(file)
      def updateFile(oldFile: File, newFile: File): Unit = onUpdateFile(oldFile, newFile)
      def renameFile(file: File, newName: String): Unit = onRenameFile(file, newName)
    }
  }

  def inherit(onAddFile: File ⇒ Unit = _ ⇒ (),
              onDeleteFile: File ⇒ Unit = _ ⇒ (),
              onUpdateFile: (File, File) ⇒ Unit = (_, _) ⇒ (),
              onRenameFile: (File, String) ⇒ Unit = (_, _) ⇒ ())(implicit fc: FileController): FileController = {
    apply(
      file ⇒ { fc.addFile(file); onAddFile(file) },
      file ⇒ { fc.deleteFile(file); onDeleteFile(file) },
      (oldFile, newFile) ⇒ { fc.updateFile(oldFile, newFile); onUpdateFile(oldFile, newFile) },
      (file, newName) ⇒ { fc.renameFile(file, newName); onRenameFile(file, newName) }
    )
  }

  def forFolderController(implicit folderController: FolderController): FileController = apply(
    file ⇒ folderController.update(file.path.parent),
    file ⇒ folderController.update(file.path.parent),
    (file, _) ⇒ folderController.update(file.path.parent),
    (file, _) ⇒ folderController.update(file.path.parent)
  )

  def forFolderContext(implicit folderContext: FolderContext): FileController = {
    forFolderController(FolderController.forFolderContext)
  }
}
