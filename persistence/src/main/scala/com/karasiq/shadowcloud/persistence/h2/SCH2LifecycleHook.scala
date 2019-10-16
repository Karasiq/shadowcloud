package com.karasiq.shadowcloud.persistence.h2

import akka.actor.ActorSystem
import com.karasiq.shadowcloud.providers.LifecycleHook

private[h2] final class SCH2LifecycleHook(actorSystem: ActorSystem) extends LifecycleHook {
  override def initialize(): Unit = {
    // Init db
    H2DB(actorSystem).context
  }

  override def shutdown(): Unit = () // Should be closed in ActorSystem terminate hook
}
