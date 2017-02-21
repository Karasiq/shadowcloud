package com.karasiq.shadowcloud.actors.internal

import akka.actor.ActorRef
import com.karasiq.shadowcloud.actors.RegionDispatcher
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.collection.mutable
import scala.language.postfixOps

private[actors] object RegionTracker {
  case class RegionStatus(regionId: String, storages: Set[String] = Set.empty)
  case class StorageStatus(storageId: String, props: StorageProps, regions: Set[String] = Set.empty)
  case class State(regions: Map[String, RegionStatus], storages: Map[String, StorageStatus])
}

private[actors] final class RegionTracker {
  import RegionTracker._
  private[this] val regions = mutable.AnyRefMap.empty[String, (RegionStatus, ActorRef)]
  private[this] val storages = mutable.AnyRefMap.empty[String, (StorageStatus, ActorRef)]

  def containsRegion(regionId: String): Boolean = {
    regions.contains(regionId)
  }

  def containsStorage(storageId: String): Boolean = {
    storages.contains(storageId)
  }

  def containsRegionAndStorage(regionId: String, storageId: String): Boolean = {
    containsRegion(regionId) && containsStorage(storageId)
  }

  def addRegion(regionId: String, dispatcher: ActorRef): Unit = {
    require(!containsRegion(regionId))
    regions += regionId → (RegionStatus(regionId), dispatcher)
  }

  def addStorage(storageId: String, props: StorageProps, dispatcher: ActorRef): Unit = {
    require(!containsStorage(storageId))
    storages += storageId → (StorageStatus(storageId, props), dispatcher)
  }

  def deleteRegion(regionId: String): ActorRef = {
    require(containsRegion(regionId))
    storages.foreach { case (storageId, (storageStatus, storage)) ⇒
      if (storageStatus.regions.contains(regionId)) {
        storages += storageId → (storageStatus.copy(regions = storageStatus.regions - regionId), storage)
      }
    }
    regions.remove(regionId).get._2
  }

  def deleteStorage(storageId: String): ActorRef = {
    require(containsStorage(storageId))
    regions.foreach { case (regionId, (regionStatus, region)) ⇒
      if (regionStatus.storages.contains(regionId)) {
        regions += regionId → (regionStatus.copy(storages = regionStatus.storages - storageId), region)
      }
    }
    storages.remove(storageId).get._2
  }

  def registerStorage(regionId: String, storageId: String): Unit = {
    require(containsRegionAndStorage(regionId, storageId))
    val (regionStatus, region) = regions(regionId)
    val (storageStatus, storage) = storages(storageId)
    regions += regionId → (regionStatus.copy(storages = regionStatus.storages + storageId), region)
    storages += storageId → (storageStatus.copy(regions = storageStatus.regions + regionId), storage)
    region ! RegionDispatcher.Register(storageId, storage, StorageHealth.empty)
  }

  def unregisterStorage(regionId: String, storageId: String): Unit = {
    require(containsRegionAndStorage(regionId, storageId))
    val (regionStatus, region) = regions(regionId)
    val (storageStatus, storage) = storages(storageId)
    regions += regionId → (regionStatus.copy(storages = regionStatus.storages - storageId), region)
    storages += storageId → (storageStatus.copy(regions = storageStatus.regions - regionId), storage)
    region ! RegionDispatcher.Unregister(storageId)
  }
}
