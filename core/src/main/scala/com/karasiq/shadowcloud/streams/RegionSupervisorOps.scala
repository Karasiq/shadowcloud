package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask

import com.karasiq.shadowcloud.actors.RegionSupervisor._
import com.karasiq.shadowcloud.actors.internal.RegionTracker
import com.karasiq.shadowcloud.config.{RegionConfig, TimeoutsConfig}
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.storage.props.StorageProps

object RegionSupervisorOps {
  def apply(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext): RegionSupervisorOps = {
    new RegionSupervisorOps(regionSupervisor, timeouts)
  }
}

final class RegionSupervisorOps(regionSupervisor: ActorRef, timeouts: TimeoutsConfig)(implicit ec: ExecutionContext) {
  def addStorage(storageId: StorageId, storageProps: StorageProps): Unit = {
    regionSupervisor ! AddStorage(storageId, storageProps)
  }

  def addRegion(regionId: RegionId, regionConfig: RegionConfig): Unit = {
    regionSupervisor ! AddRegion(regionId, regionConfig)
  }

  def register(regionId: RegionId, storageId: StorageId): Unit = {
    regionSupervisor ! RegisterStorage(regionId, storageId)
  }

  def unregister(regionId: RegionId, storageId: StorageId): Unit = {
    regionSupervisor ! UnregisterStorage(regionId, storageId)
  }

  def deleteStorage(storageId: StorageId): Unit = {
    regionSupervisor ! DeleteStorage(storageId)
  }

  def deleteRegion(regionId: RegionId): Unit = {
    regionSupervisor ! DeleteRegion(regionId)
  }

  def suspendStorage(storageId: StorageId): Unit = {
    regionSupervisor ! SuspendStorage(storageId)
  }

  def suspendRegion(regionId: RegionId): Unit = {
    regionSupervisor ! SuspendRegion(regionId)
  }

  def resumeStorage(storageId: StorageId): Unit = {
    regionSupervisor ! ResumeStorage(storageId)
  }

  def resumeRegion(regionId: RegionId): Unit = {
    regionSupervisor ! ResumeRegion(regionId)
  }

  def getSnapshot(): Future[RegionTracker.Snapshot] = {
    GetSnapshot.unwrapFuture(regionSupervisor.ask(GetSnapshot)(timeouts.query))
  }
}
