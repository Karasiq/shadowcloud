package com.karasiq.shadowcloud.webapp.controllers

import com.karasiq.shadowcloud.model.File

trait FileController {
  def addFile(file: File): Unit
  def deleteFile(file: File): Unit
  def updateFile(oldFile: File, newFile: File): Unit
  def renameFile(file: File, newName: String): Unit
}

object FileController {
  def apply(onAddFile: File ⇒ Unit, onDeleteFile: File ⇒ Unit,
            onUpdateFile: (File, File) ⇒ Unit, onRenameFile: (File, String) ⇒ Unit): FileController = {
    new FileController {
      def addFile(file: File): Unit = onAddFile(file)
      def deleteFile(file: File): Unit = onDeleteFile(file)
      def updateFile(oldFile: File, newFile: File): Unit = onUpdateFile(oldFile, newFile)
      def renameFile(file: File, newName: String): Unit = onRenameFile(file, newName)
    }
  }
}
