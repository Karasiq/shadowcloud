package com.karasiq.shadowcloud.ui

trait UIProvider {
  def showErrorMessage(error: Throwable): Unit
}
