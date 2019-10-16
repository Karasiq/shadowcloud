package com.karasiq.shadowcloud.internal

import akka.actor.ActorSystem
import com.karasiq.shadowcloud.providers.LifecycleHook

private class StdLifecycleHook(actorSystem: ActorSystem) extends LifecycleHook {
  override def initialize(): Unit = {
    if (actorSystem.log.isDebugEnabled)
      actorSystem.logConfiguration() // log-config-on-start = on
  }

  override def shutdown(): Unit = {
    actorSystem.terminate()
  }
}
