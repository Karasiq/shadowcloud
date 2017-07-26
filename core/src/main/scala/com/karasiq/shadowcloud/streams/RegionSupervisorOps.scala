package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.RegionSupervisor._
import com.karasiq.shadowcloud.actors.internal.RegionTracker
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.storage.props.StorageProps

object RegionSupervisorOps {
  def apply(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): RegionSupervisorOps = {
    new RegionSupervisorOps(regionSupervisor)
  }
}

final class RegionSupervisorOps(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {
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

  def getSnapshot(): Future[RegionTracker.Snapshot] = {
    GetSnapshot.unwrapFuture(regionSupervisor ? GetSnapshot)
  }
}
