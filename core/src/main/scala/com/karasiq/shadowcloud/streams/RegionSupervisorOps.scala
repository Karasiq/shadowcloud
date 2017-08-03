package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask

import com.karasiq.shadowcloud.actors.RegionSupervisor._
import com.karasiq.shadowcloud.actors.internal.RegionTracker
import com.karasiq.shadowcloud.config.{RegionConfig, TimeoutsConfig}
import com.karasiq.shadowcloud.storage.props.StorageProps

object RegionSupervisorOps {
  def apply(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext): RegionSupervisorOps = {
    new RegionSupervisorOps(regionSupervisor, timeouts)
  }
}

final class RegionSupervisorOps(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext) {
  def addStorage(storageId: String, storageProps: StorageProps): Unit = {
    regionSupervisor ! AddStorage(storageId, storageProps)
  }

  def addRegion(regionId: String, regionConfig: RegionConfig): Unit = {
    regionSupervisor ! AddRegion(regionId, regionConfig)
  }

  def register(regionId: String, storageId: String): Unit = {
    regionSupervisor ! RegisterStorage(regionId, storageId)
  }

  def unregister(regionId: String, storageId: String): Unit = {
    regionSupervisor ! UnregisterStorage(regionId, storageId)
  }

  def deleteStorage(storageId: String): Unit = {
    regionSupervisor ! DeleteStorage(storageId)
  }

  def deleteRegion(regionId: String): Unit = {
    regionSupervisor ! DeleteRegion(regionId)
  }

  def suspendStorage(storageId: String): Unit = {
    regionSupervisor ! SuspendStorage(storageId)
  }

  def suspendRegion(regionId: String): Unit = {
    regionSupervisor ! SuspendRegion(regionId)
  }

  def resumeStorage(storageId: String): Unit = {
    regionSupervisor ! ResumeStorage(storageId)
  }

  def resumeRegion(regionId: String): Unit = {
    regionSupervisor ! ResumeRegion(regionId)
  }

  def getSnapshot(): Future[RegionTracker.Snapshot] = {
    GetSnapshot.unwrapFuture(regionSupervisor.ask(GetSnapshot)(timeouts.query))
  }
}
