package com.karasiq.shadowcloud.providers

trait LifecycleHook {
  def initialize(): Unit
  def shutdown(): Unit
}

object LifecycleHook {
  def initialize(f: => Unit): LifecycleHook = new LifecycleHook {
    override def initialize(): Unit = f
    override def shutdown(): Unit = ()
  }

  def shutdown(f: => Unit): LifecycleHook = new LifecycleHook {
    override def initialize(): Unit = ()
    override def shutdown(): Unit = f
  }
}
