package com.karasiq.shadowcloud.webapp.locales

trait AppLocale {
  def fileId: String
  def fileName: String
  def fileSize: String
}

object AppLocale {
  object English extends AppLocale {
    val fileId = "File ID"
    val fileName = "Name"
    val fileSize = "Size"
  }

  val default: AppLocale = English
}