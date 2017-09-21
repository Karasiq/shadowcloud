package com.karasiq.shadowcloud.webapp.locales

import com.karasiq.shadowcloud.model.Path

trait AppLocale {
  def languageCode: String

  def name: String
  def path: String
  def value: String
  def config: String
  def size: String
  def empty: String
  def show: String
  def edit: String
  def submit: String
  def cancel: String
  def close: String
  def unknown: String
  def total: String
  def entries(value: Long): String

  def file: String
  def folder: String

  def delete: String
  def move: String
  def copy: String
  def rename: String

  def rootPath: String
  def emptyPath: String
  def createFolder: String
  def deleteFolder: String

  def video: String
  def image: String
  def audio: String

  def uploadFiles: String
  def downloadFile: String
  def deleteFile: String
  def playFile: String
  def viewTextFile: String

  def fileId: String
  def createdDate: String
  def modifiedDate: String
  def revision: String

  def preview: String
  def metadata: String
  def content: String

  def availability: String
  def revisions: String

  def foldersView: String
  def regionsView: String

  def storage: String
  def storages: String
  def region: String
  def regions: String
  def indexScope: String
  def currentScope: String
  def historyScope: String
  def indexSnapshotDate: String

  def collectGarbage: String
  def compactIndex: String

  def writableSpace: String
  def totalSpace: String
  def freeSpace: String
  def usedSpace: String

  def regionId: String
  def storageId: String
  def storageType: String
  def createRegion: String
  def createStorage: String
  def registerStorage: String
  def registerRegion: String
  def unregisterStorage: String
  def unregisterRegion: String
  def suspend: String
  def resume: String

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
    val config = "Config"
    val size = "Size"
    val empty = "Empty"
    val show = "Show"
    val edit = "Edit"
    val submit = "Submit"
    val cancel = "Cancel"
    val close = "Close"
    val unknown = "Unknown"
    val total = "Total"
    def entries(value: Long): String = value + " entries"

    val file = "File"
    val folder = "Folder"

    val delete = "Delete"
    val move = "Move"
    val copy = "Copy"
    val rename = "Rename"

    val rootPath = "(Root)"
    val emptyPath = "(Empty)"
    val createFolder = "Create folder"
    val deleteFolder = "Delete folder"

    val image = "Image"
    val audio = "Audio"
    val video = "Video"

    val uploadFiles = "Upload"
    val downloadFile = "Download"
    val deleteFile = "Delete"
    val playFile = "Play"
    val viewTextFile = "View text"

    val fileId = "File ID"
    val createdDate = "Created"
    val modifiedDate = "Last modified"
    val revision = "Revision"

    val preview = "Preview"
    val metadata = "Metadata"
    val content = "Content"

    val availability = "Availability"
    val revisions = "Revisions"

    val foldersView = "Folders"
    val regionsView = "Regions"

    val storage = "Storage"
    val storages = "Storages"
    val region = "Region"
    val regions = "Regions"
    val indexScope = "Index scope"
    val currentScope = "Current"
    val historyScope = "History"
    val indexSnapshotDate = "Snapshot date"

    val collectGarbage = "Collect garbage"
    val compactIndex = "Compact index"

    val writableSpace = "Writable space"
    val totalSpace = "Total space"
    val freeSpace = "Free space"
    val usedSpace = "Used space"
    
    val regionId = "Region ID"
    val storageId = "Storage ID"
    val storageType = "Storage type"
    val createRegion = "Create region"
    val createStorage = "Create storage"
    val registerStorage = "Register storage"
    val registerRegion = "Register region"
    val unregisterStorage = "Unregister storage"
    val unregisterRegion = "Unregister region"
    val suspend = "Suspend"
    val resume = "Resume"

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