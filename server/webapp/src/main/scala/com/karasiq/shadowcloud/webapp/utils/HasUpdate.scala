package com.karasiq.shadowcloud.webapp.utils

trait HasUpdate {
  def update(): Unit
}

trait HasKeyUpdate[K] {
  def update(key: K): Unit
}
