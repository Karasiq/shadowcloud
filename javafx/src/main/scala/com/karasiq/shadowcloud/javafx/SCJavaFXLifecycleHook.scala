package com.karasiq.shadowcloud.javafx

import akka.actor.ActorSystem
import com.karasiq.shadowcloud.providers.LifecycleHook

import scala.concurrent.Await

private[javafx] final class SCJavaFXLifecycleHook(actorSystem: ActorSystem) extends LifecycleHook {
  override def initialize(): Unit = {
    import scala.concurrent.duration._
    Await.ready(JavaFXContext(actorSystem).initFuture, 30 seconds)
  }

  override def shutdown(): Unit = ()
}
