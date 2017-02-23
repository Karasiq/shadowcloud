package com.karasiq.shadowcloud.actors.internal

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.actors.RegionDispatcher
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.collection.mutable
import scala.language.postfixOps

private[actors] object RegionTracker {
  case class RegionStatus(regionId: String, dispatcher: ActorRef, storages: Set[String] = Set.empty)
  case class StorageStatus(storageId: String, props: StorageProps, dispatcher: ActorRef, regions: Set[String] = Set.empty)

  def apply()(implicit context: ActorContext): RegionTracker = {
    new RegionTracker
  }
}

private[actors] final class RegionTracker(implicit context: ActorContext) {
  import RegionTracker._

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  val regions = mutable.AnyRefMap.empty[String, RegionStatus]
  val storages = mutable.AnyRefMap.empty[String, StorageStatus]

  // -----------------------------------------------------------------------
  // Contains
  // -----------------------------------------------------------------------
  def containsRegion(regionId: String): Boolean = {
    regions.contains(regionId)
  }

  def containsStorage(storageId: String): Boolean = {
    storages.contains(storageId)
  }

  def containsRegionAndStorage(regionId: String, storageId: String): Boolean = {
    containsRegion(regionId) && containsStorage(storageId)
  }

  // -----------------------------------------------------------------------
  // Add
  // -----------------------------------------------------------------------
  def addRegion(regionId: String, dispatcher: ActorRef): Unit = {
    require(!containsRegion(regionId))
    regions += regionId → RegionStatus(regionId, dispatcher)
  }

  def addStorage(storageId: String, props: StorageProps, dispatcher: ActorRef): Unit = {
    require(!containsStorage(storageId))
    storages += storageId → StorageStatus(storageId, props, dispatcher)
  }

  // -----------------------------------------------------------------------
  // Delete
  // -----------------------------------------------------------------------
  def deleteRegion(regionId: String): RegionStatus = {
    require(containsRegion(regionId))
    storages.foreach { case (storageId, storage) ⇒
      if (storage.regions.contains(regionId))
        storages += storageId → storage.copy(regions = storage.regions - regionId)
    }
    val status = regions.remove(regionId).get
    context.stop(status.dispatcher)
    status
  }

  def deleteStorage(storageId: String): StorageStatus = {
    require(containsStorage(storageId))
    regions.foreach { case (regionId, region) ⇒
      if (region.storages.contains(storageId)) {
        regions += regionId → region.copy(storages = region.storages - storageId)
        region.dispatcher ! RegionDispatcher.Unregister(storageId)
      }
    }
    val status = storages.remove(storageId).get
    context.stop(status.dispatcher)
    status
  }

  def clear(): Unit = {
    regions.foreachValue(region ⇒ context.stop(region.dispatcher))
    storages.foreachValue(storage ⇒ context.stop(storage.dispatcher))
    regions.clear()
    storages.clear()
  }

  // -----------------------------------------------------------------------
  // Register/unregister
  // -----------------------------------------------------------------------
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
