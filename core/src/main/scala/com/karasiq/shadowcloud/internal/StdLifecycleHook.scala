package com.karasiq.shadowcloud.internal

import akka.actor.ActorSystem
import com.karasiq.shadowcloud.providers.LifecycleHook

private class StdLifecycleHook(actorSystem: ActorSystem) extends LifecycleHook {
  override def initialize(): Unit = ()

  override def shutdown(): Unit = {
    actorSystem.terminate()
  }
}
