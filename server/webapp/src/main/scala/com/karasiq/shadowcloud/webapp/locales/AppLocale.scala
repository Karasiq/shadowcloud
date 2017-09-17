package com.karasiq.shadowcloud.webapp.locales

import com.karasiq.shadowcloud.model.Path

trait AppLocale {
  def languageCode: String

  def name: String
  def path: String
  def value: String
  def size: String
  def empty: String
  def show: String
  def submit: String
  def cancel: String
  def close: String
  def unknown: String
  def entries(value: Long): String

  def file: String
  def folder: String

  def rootPath: String
  def emptyPath: String
  def createFolder: String
  def deleteFolder: String

  def downloadFile: String
  def deleteFile: String
  def playFile: String

  def fileId: String
  def createdDate: String
  def modifiedDate: String
  def revision: String

  def preview: String
  def metadata: String
  def content: String

  def availability: String
  def revisions: String

  def storage: String
  def region: String
  def indexScope: String
  def currentScope: String
  def historyScope: String
  def indexSnapshotDate: String

  def metadataParser: String
  def metadataDisposition: String
  def metadataType: String
  def metadataFormat: String

  def metadataText: String
  def metadataTable: String
  def metadataFileList: String
  def metadataThumbnail: String
  def metadataImageData: String
  def metadataEmbeddedResources: String

  def deleteFolderConfirmation(path: Path): String
}

object AppLocale {
  object English extends AppLocale {
    val languageCode = "en"

    val name = "Name"
    val path = "Path"
    val value = "Value"
    val size = "Size"
    val empty = "Empty"
    val show = "Show"
    val submit = "Submit"
    val cancel = "Cancel"
    val close = "Close"
    val unknown = "Unknown"
    def entries(value: Long): String = value + " entries"

    val file = "File"
    val folder = "Folder"

    val rootPath = "(Root)"
    val emptyPath = "(Empty)"
    val createFolder = "Create folder"
    val deleteFolder = "Delete folder"

    val downloadFile = "Download"
    val deleteFile = "Delete"
    val playFile = "Play"

    val fileId = "File ID"
    val createdDate = "Created"
    val modifiedDate = "Last modified"
    val revision = "Revision"

    val preview = "Preview"
    val metadata = "Metadata"
    val content = "Content"

    val availability = "Availability"
    val revisions = "Revisions"

    val storage = "Storage"
    val region = "Region"
    val indexScope = "Index scope"
    val currentScope = "Current"
    val historyScope = "History"
    val indexSnapshotDate = "Snapshot date"

    val metadataParser = "Parser"
    val metadataDisposition = "Disposition"
    val metadataType = "Type"
    val metadataFormat = "Format"

    val metadataText = "Text"
    val metadataTable = "Table"
    val metadataFileList = "File list"
    val metadataThumbnail = "Thumbnail"
    val metadataImageData = "Image data"
    val metadataEmbeddedResources = "Embedded resources"

    def deleteFolderConfirmation(path: Path): String = {
      s"Are you sure you want to delete folder $path?"
    }
  }

  val default: AppLocale = English
}