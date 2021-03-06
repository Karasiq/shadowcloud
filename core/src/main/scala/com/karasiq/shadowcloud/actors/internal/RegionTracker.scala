package com.karasiq.shadowcloud.actors.internal

import akka.actor.ActorContext
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.{ActorState => State}
import com.karasiq.shadowcloud.actors.{RegionContainer, RegionDispatcher, StorageContainer, StorageIndex}
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.collection.mutable

object RegionTracker {
  final case class RegionStatus(regionId: RegionId, regionConfig: RegionConfig, actorState: State, storages: Set[String] = Set.empty)
  final case class StorageStatus(storageId: StorageId, storageProps: StorageProps, actorState: State, regions: Set[String] = Set.empty)

  @SerialVersionUID(0L)
  final case class Snapshot(regions: Map[String, RegionStatus], storages: Map[String, StorageStatus])

  private[actors] def apply()(implicit context: ActorContext): RegionTracker = {
    new RegionTracker
  }
}

private[actors] final class RegionTracker(implicit context: ActorContext) {
  import RegionTracker._

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] val sc           = ShadowCloud()
  private[this] val instantiator = StorageInstantiator(sc.modules)
  private[this] val regions      = mutable.AnyRefMap.empty[String, RegionStatus]
  private[this] val storages     = mutable.AnyRefMap.empty[String, StorageStatus]

  // -----------------------------------------------------------------------
  // Contains
  // -----------------------------------------------------------------------
  def containsRegion(regionId: RegionId): Boolean = {
    regions.contains(regionId)
  }

  def containsStorage(storageId: StorageId): Boolean = {
    storages.contains(storageId)
  }

  def containsRegionAndStorage(regionId: RegionId, storageId: StorageId): Boolean = {
    containsRegion(regionId) && containsStorage(storageId)
  }

  // -----------------------------------------------------------------------
  // Get
  // -----------------------------------------------------------------------
  def getStorage(storageId: StorageId): StorageStatus = {
    storages(storageId)
  }

  def getRegion(regionId: RegionId): RegionStatus = {
    regions(regionId)
  }

  def getSnapshot(): Snapshot = {
    Snapshot(regions.toMap, storages.toMap)
  }

  // -----------------------------------------------------------------------
  // Add
  // -----------------------------------------------------------------------
  def addRegion(regionId: RegionId, config: RegionConfig): Unit = {
    val status = regions.get(regionId)
    val state = status
      .map(_.actorState)
      .getOrElse(State.Suspended)

    State.ifActive(state, _ ! RegionContainer.SetConfig(config))

    val newStatus = status.fold(RegionStatus(regionId, config, state))(_.copy(regionConfig = config))
    regions += regionId → newStatus
  }

  def addStorage(storageId: StorageId, props: StorageProps): Unit = {
    val status = storages.get(storageId)
    val state = status
      .map(_.actorState)
      .getOrElse(State.Suspended)

    State.ifActive(state, _ ! StorageContainer.SetProps(props))

    val newStatus = status.fold(StorageStatus(storageId, props, state))(_.copy(storageProps = props))
    storages += storageId → newStatus
  }

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  def suspendStorage(storageId: StorageId): Unit = {
    storages.get(storageId) match {
      case Some(storage @ StorageStatus(`storageId`, _, State.Active(dispatcher), regions)) ⇒
        regions
          .flatMap(this.regions.get)
          .foreach(region ⇒ State.ifActive(region.actorState, _ ! RegionDispatcher.DetachStorage(storageId)))
        context.unwatch(dispatcher)
        context.stop(dispatcher)
        storages += storageId → storage.copy(actorState = State.Suspended)

      case _ ⇒
      // Pass
    }
  }

  def suspendRegion(regionId: RegionId): Unit = {
    regions.get(regionId) match {
      case Some(region @ RegionStatus(`regionId`, _, State.Active(dispatcher), storages)) ⇒
        storages
          .flatMap(this.storages.get)
          .foreach(storage ⇒ State.ifActive(storage.actorState, _ ! StorageIndex.CloseIndex(regionId)))
        context.unwatch(dispatcher)
        context.stop(dispatcher)
        regions += regionId → region.copy(actorState = State.Suspended)

      case _ ⇒
      // Pass
    }
  }

  def resumeStorage(storageId: StorageId): Unit = {
    storages.get(storageId) match {
      case Some(StorageStatus(`storageId`, props, State.Suspended, regions)) ⇒
        val dispatcherProps = StorageContainer.props(instantiator, storageId)
        val dispatcher      = context.watch(context.actorOf(dispatcherProps))
        dispatcher ! StorageContainer.SetProps(props)
        storages += storageId → StorageStatus(storageId, props, State.Active(dispatcher), regions)
      // registerStorageRegions(storageId)

      case _ ⇒
      // Pass
    }
  }

  def resumeRegion(regionId: RegionId): Unit = {
    regions.get(regionId) match {
      case Some(RegionStatus(`regionId`, config, State.Suspended, storages)) ⇒
        val dispatcherProps = RegionContainer.props(regionId)
        val dispatcher      = context.watch(context.actorOf(dispatcherProps))
        dispatcher ! RegionContainer.SetConfig(config)
        regions += regionId → RegionStatus(regionId, config, State.Active(dispatcher), storages)
      // registerRegionStorages(regionId)

      case _ ⇒
      // Pass
    }
  }

  // -----------------------------------------------------------------------
  // Delete
  // -----------------------------------------------------------------------
  def deleteRegion(regionId: RegionId): RegionStatus = {
    require(containsRegion(regionId))
    storages.foreach {
      case (storageId, storage) ⇒
        if (storage.regions.contains(regionId))
          storages += storageId → storage.copy(regions = storage.regions - regionId)
    }
    val status = regions.remove(regionId).get
    State.ifActive(status.actorState, { dispatcher ⇒
      context.unwatch(dispatcher)
      context.stop(dispatcher)
    })
    status.storages.flatMap(storages.get).foreach { storage ⇒
      State.ifActive(storage.actorState, _ ! StorageIndex.CloseIndex(regionId, clear = true))
    }
    status
  }

  def deleteStorage(storageId: StorageId): StorageStatus = {
    require(containsStorage(storageId))
    regions.foreach {
      case (regionId, region) ⇒
        if (region.storages.contains(storageId)) {
          regions += regionId → region.copy(storages = region.storages - storageId)
          State.ifActive(region.actorState, _ ! RegionDispatcher.DetachStorage(storageId))
        }
    }
    val status = storages.remove(storageId).get
    State.ifActive(status.actorState, { dispatcher ⇒
      context.unwatch(dispatcher)
      context.stop(dispatcher)
    })
    status
  }

  def clear(): Unit = {
    (regions.values.map(_.actorState) ++ storages.values.map(_.actorState)).foreach(State.ifActive(_, { dispatcher ⇒
      context.unwatch(dispatcher)
      context.stop(dispatcher)
    }))
    regions.clear()
    storages.clear()
  }

  // -----------------------------------------------------------------------
  // Register/unregister
  // -----------------------------------------------------------------------
  def registerStorage(regionId: RegionId, storageId: StorageId): Unit = {
    require(containsRegionAndStorage(regionId, storageId))
    val region  = regions(regionId)
    val storage = storages(storageId)
    regions += regionId   → region.copy(storages = region.storages + storageId)
    storages += storageId → storage.copy(regions = storage.regions + regionId)

    (storage.actorState, region.actorState) match {
      case (State.Active(storageDispatcher), State.Active(regionDispatcher)) ⇒
        storageDispatcher ! StorageIndex.OpenIndex(regionId)
        regionDispatcher ! RegionDispatcher.AttachStorage(storageId, storage.storageProps, storageDispatcher, StorageHealth.empty)

      case _ ⇒
      // Pass
    }
  }

  def unregisterStorage(regionId: RegionId, storageId: StorageId): Unit = {
    require(containsRegionAndStorage(regionId, storageId))
    val region  = regions(regionId)
    val storage = storages(storageId)
    regions += regionId   → region.copy(storages = region.storages - storageId)
    storages += storageId → storage.copy(regions = storage.regions - regionId)
    State.ifActive(region.actorState, _ ! RegionDispatcher.DetachStorage(storageId))
    State.ifActive(storage.actorState, _ ! StorageIndex.CloseIndex(regionId, clear = true))
  }

  def registerRegionStorages(regionId: RegionId): Unit = {
    regions.get(regionId) match {
      case Some(region) ⇒
        region.storages.foreach(registerStorage(regionId, _))

      case None ⇒
      // Pass
    }
  }

  def registerStorageRegions(storageId: StorageId): Unit = {
    storages.get(storageId) match {
      case Some(storage) ⇒
        storage.regions.foreach(registerStorage(_, storageId))

      case None ⇒
      // Pass
    }
  }
}
