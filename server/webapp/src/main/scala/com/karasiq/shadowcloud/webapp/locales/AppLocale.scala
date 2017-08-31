package com.karasiq.shadowcloud.webapp.locales

trait AppLocale {
  def languageCode: String

  def name: String
  def value: String
  def size: String
  def show: String
  def cancel: String
  def close: String
  def unknown: String
  def entries(value: Long): String

  def rootPath: String
  def fileId: String
  def createdDate: String
  def modifiedDate: String
  def revision: String

  def preview: String
  def metadata: String
  def content: String

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
}

object AppLocale {
  object English extends AppLocale {
    val languageCode = "en"

    val name = "Name"
    val value = "Value"
    val size = "Size"
    val show = "Show"
    val cancel = "Cancel"
    val close = "Close"
    val unknown = "Unknown"
    def entries(value: Long): String = value.toString + " entries"

    val rootPath = "(Root)"
    val fileId = "File ID"
    val createdDate = "Created"
    val modifiedDate = "Last modified"
    val revision = "Revision"

    val preview = "Preview"
    val metadata = "Metadata"
    val content = "Content"

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
  }

  val default: AppLocale = English
}