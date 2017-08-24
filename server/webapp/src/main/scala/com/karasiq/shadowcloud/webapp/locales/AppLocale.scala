package com.karasiq.shadowcloud.webapp.locales

trait AppLocale {
  def languageCode: String
  def fileId: String
  def name: String
  def size: String
  def createdDate: String
  def modifiedDate: String
}

object AppLocale {
  object English extends AppLocale {
    val languageCode = "en"
    val fileId = "File ID"
    val name = "Name"
    val size = "Size"
    val createdDate = "Created"
    val modifiedDate = "Last modified"
  }

  val default: AppLocale = English
}