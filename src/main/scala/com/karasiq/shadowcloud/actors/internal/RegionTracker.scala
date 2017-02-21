package com.karasiq.shadowcloud.actors.internal

import akka.actor.ActorRef
import com.karasiq.shadowcloud.actors.RegionDispatcher
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.collection.mutable
import scala.language.postfixOps

private[actors] object RegionTracker {
  case class RegionStatus(regionId: String, dispatcher: ActorRef, storages: Set[String] = Set.empty)
  case class StorageStatus(storageId: String, props: StorageProps, dispatcher: ActorRef, regions: Set[String] = Set.empty)
}

private[actors] final class RegionTracker {
  import RegionTracker._
  val regions = mutable.AnyRefMap.empty[String, RegionStatus]
  val storages = mutable.AnyRefMap.empty[String, StorageStatus]

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
    regions += regionId → RegionStatus(regionId, dispatcher)
  }

  def addStorage(storageId: String, props: StorageProps, dispatcher: ActorRef): Unit = {
    require(!containsStorage(storageId))
    storages += storageId → StorageStatus(storageId, props, dispatcher)
  }

  def deleteRegion(regionId: String): RegionStatus = {
    require(containsRegion(regionId))
    storages.foreach { case (storageId, storage) ⇒
      if (storage.regions.contains(regionId)) {
        storages += storageId → storage.copy(regions = storage.regions - regionId)
      }
    }
    regions.remove(regionId).get
  }

  def deleteStorage(storageId: String): StorageStatus = {
    require(containsStorage(storageId))
    regions.foreach { case (regionId, region) ⇒
      if (region.storages.contains(regionId)) {
        regions += regionId → region.copy(storages = region.storages - storageId)
      }
    }
    storages.remove(storageId).get
  }

  def registerStorage(regionId: String, storageId: String): Unit = {
    require(containsRegionAndStorage(regionId, storageId))
    val region = regions(regionId)
    val storage = storages(storageId)
    regions += regionId → region.copy(storages = region.storages + storageId)
    storages += storageId → storage.copy(regions = storage.regions + regionId)
    region.dispatcher ! RegionDispatcher.Register(storageId, storage.dispatcher, StorageHealth.empty)
  }

  def unregisterStorage(regionId: String, storageId: String): Unit = {
    require(containsRegionAndStorage(regionId, storageId))
    val region = regions(regionId)
    val storage = storages(storageId)
    regions += regionId → region.copy(storages = region.storages - storageId)
    storages += storageId → storage.copy(regions = storage.regions - regionId)
    region.dispatcher ! RegionDispatcher.Unregister(storageId)
  }
}
