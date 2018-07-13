package com.karasiq.shadowcloud.webapp.locales.impl

import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.locales.AppLocale

private[locales] object English extends AppLocale {
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
  val repairFile = "Repair"
  val playFile = "Play"
  val viewTextFile = "View text"
  val inspectFile = "Inspect"
  val pasteText = "Paste text"

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
  val repairRegion = "Repair region"

  val writableSpace = "Writable space"
  val totalSpace = "Total space"
  val freeSpace = "Free space"
  val usedSpace = "Used space"

  val regionId = "Region ID"
  val regionIdHint = "Hint: format [a-z0-9_-] is recommended"
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

  val keys = "Keys"
  val keyId = "Key ID"
  val keyEncAlgorithm = "Encryption algorithm"
  val keySignAlgorithm = "Signature algorithm"
  val keyPermissions = "Key permissions"
  val keyForEncryption = "Encryption"
  val keyForDecryption = "Decryption"
  val generateKey = "Generate key"
  val exportKey = "Export key"
  val importKey = "Import key"

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