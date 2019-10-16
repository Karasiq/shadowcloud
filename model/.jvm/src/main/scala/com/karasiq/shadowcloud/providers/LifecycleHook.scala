package com.karasiq.shadowcloud.providers

trait LifecycleHook {
  def initialize(): Unit
  def shutdown(): Unit
}
