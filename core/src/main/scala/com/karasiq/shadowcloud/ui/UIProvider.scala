package com.karasiq.shadowcloud.ui

trait UIProvider {
  def showNotification(text: String): Unit
  def showErrorMessage(error: Throwable): Unit
  def canBlock: Boolean
}
