package com.karasiq.shadowcloud.providers

trait ModuleProvider {
  def defaultName: String = {
    getClass.getName
  }
}
