package com.karasiq.shadowcloud.internal

import akka.{Done, NotUsed}
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.actors.utils.ActorState
import com.karasiq.shadowcloud.providers.LifecycleHook

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

private class StdLifecycleHook(sc: ShadowCloudExtension) extends LifecycleHook {
  override def initialize(): Unit = ()

  override def shutdown(): Unit = {
    import sc.implicits.executionContext

    val syncFuture = if (sc.config.misc.syncOnExit) {
     for {
       regions <- sc.ops.supervisor.getSnapshot()
       _ <- Future.sequence(
         regions.regions.filter(_._2.actorState.isInstanceOf[ActorState.Active]).keys.map(regionId => sc.ops.region.synchronize(regionId))
       )
     } yield Done
    } else Future.successful(Done)

    val future = for {
      _ <- syncFuture
      _ = sc.implicits.actorSystem.stop(sc.actors.regionSupervisor)
    } yield NotUsed

    Await.result(future, 1 minute)
  }
}
