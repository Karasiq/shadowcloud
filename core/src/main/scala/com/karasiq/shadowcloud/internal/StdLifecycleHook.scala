package com.karasiq.shadowcloud.internal

import akka.NotUsed
import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.actors.utils.ActorState
import com.karasiq.shadowcloud.providers.LifecycleHook

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

private class StdLifecycleHook(sc: ShadowCloudExtension) extends LifecycleHook {
  override def initialize(): Unit = ()

  override def shutdown(): Unit = {
    import sc.implicits.executionContext

    val future = for {
      regions <- sc.ops.supervisor.getSnapshot()
      _ <- Future.sequence(
        regions.regions.filter(_._2.actorState.isInstanceOf[ActorState.Active]).keys.map(regionId => sc.ops.region.synchronize(regionId))
      )
      _ = sc.implicits.actorSystem.stop(sc.actors.regionSupervisor)
    } yield NotUsed

    Await.result(future, 1 minute)
  }
}
