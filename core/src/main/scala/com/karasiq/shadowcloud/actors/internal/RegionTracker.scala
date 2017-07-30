package com.karasiq.shadowcloud.actors.internal

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.ActorContext
import akka.pattern.BackoffSupervisor

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.{RegionContainer, RegionDispatcher, StorageContainer, StorageIndex}
import com.karasiq.shadowcloud.actors.utils.{ActorState ⇒ State}
import com.karasiq.shadowcloud.config.RegionConfig
import com.karasiq.shadowcloud.storage.StorageHealth
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.utils.Utils

object RegionTracker {
  case class RegionStatus(regionId: String, regionConfig: RegionConfig, actorState: State, storages: Set[String] = Set.empty)
  case class StorageStatus(storageId: String, props: StorageProps, actorState: State, regions: Set[String] = Set.empty)

  case class Snapshot(regions: Map[String, RegionStatus], storages: Map[String, StorageStatus])

  private[actors] def apply()(implicit context: ActorContext): RegionTracker = {
    new RegionTracker
  }
}

private[actors] final class RegionTracker(implicit context: ActorContext) {
  import RegionTracker._

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] val sc = ShadowCloud()
  private[this] val instantiator = StorageInstantiator(sc.modules)
  private[this] val regions = mutable.AnyRefMap.empty[String, RegionStatus]
  private[this] val storages = mutable.AnyRefMap.empty[String, StorageStatus]

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
  // Get
  // -----------------------------------------------------------------------
  def getStorage(storageId: String): StorageStatus = {
    storages(storageId)
  }

  def getRegion(regionId: String): RegionStatus = {
    regions(regionId)
  }

  def getSnapshot(): Snapshot = {
    Snapshot(regions.toMap, storages.toMap)
  }

  // -----------------------------------------------------------------------
  // Add
  // -----------------------------------------------------------------------
  def addRegion(regionId: String, config: RegionConfig): Unit = {
    val state = regions.get(regionId)
      .map(_.actorState)
      .getOrElse(State.Suspended)

    State.ifActive(state, _ ! RegionContainer.SetConfig(config))
    regions += regionId → RegionStatus(regionId, config, state)
  }

  def addStorage(storageId: String, props: StorageProps): Unit = {
    val state = storages.get(storageId)
      .map(_.actorState)
      .getOrElse(State.Suspended)

    State.ifActive(state, _ ! StorageContainer.SetProps(props))
    storages += storageId → StorageStatus(storageId, props, state)
  }

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  def suspendStorage(storageId: String): Unit = {
    storages.get(storageId) match {
      case Some(status @ StorageStatus(`storageId`, _, State.Active(dispatcher), regions)) ⇒
        regions.flatMap(this.regions.get)
          .foreach(region ⇒ State.ifActive(region.actorState, _ ! RegionDispatcher.DetachStorage(storageId)))
        context.stop(dispatcher)
        storages += storageId → status.copy(actorState = State.Suspended)

      case _ ⇒
        // Pass
    }
  }

  def suspendRegion(regionId: String): Unit = {
    regions.get(regionId) match {
      case Some(status @ RegionStatus(`regionId`, _, State.Active(dispatcher), storages)) ⇒
        storages.flatMap(this.storages.get)
          .foreach(storage ⇒ State.ifActive(storage.actorState, _ ! StorageIndex.CloseIndex(regionId)))
        context.stop(dispatcher)
        regions += regionId → status.copy(actorState = State.Suspended)

      case _ ⇒
        // Pass
    }
  }

  def resumeStorage(storageId: String): Unit = {
    storages.get(storageId) match {
      case Some(StorageStatus(`storageId`, props, State.Suspended, regions)) ⇒
        val dispatcherProps = StorageContainer.props(instantiator, storageId)
        val supervisorProps = BackoffSupervisor.props(dispatcherProps, "container", 1 second, 1 minute, 0.2)
        val dispatcher = context.actorOf(supervisorProps, Utils.uniqueActorName(s"$storageId-sv"))
        dispatcher ! StorageContainer.SetProps(props)
        storages += storageId → StorageStatus(storageId, props, State.Active(dispatcher))
        regions.foreach(registerStorage(_, storageId))

      case _ ⇒
        // Pass
    }
  }

  def resumeRegion(regionId: String): Unit = {
    regions.get(regionId) match {
      case Some(RegionStatus(`regionId`, config, State.Suspended, storages)) ⇒
        val dispatcherProps = RegionContainer.props(regionId)
        val supervisorProps = BackoffSupervisor.props(dispatcherProps, "container", 1 second, 1 minute, 0.2)
        val dispatcher = context.actorOf(supervisorProps, Utils.uniqueActorName(s"$regionId-sv"))
        dispatcher ! RegionContainer.SetConfig(config)
        regions += regionId → RegionStatus(regionId, config, State.Active(dispatcher))
        registerRegionStorages(regionId)

      case _ ⇒
        // Pass
    }
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
    State.ifActive(status.actorState, context.stop)
    status
  }

  def deleteStorage(storageId: String): StorageStatus = {
    require(containsStorage(storageId))
    regions.foreach { case (regionId, region) ⇒
      if (region.storages.contains(storageId)) {
        regions += regionId → region.copy(storages = region.storages - storageId)
        State.ifActive(region.actorState, _ ! RegionDispatcher.DetachStorage(storageId))
      }
    }
    val status = storages.remove(storageId).get
    State.ifActive(status.actorState, context.stop)
    status
  }

  def clear(): Unit = {
    regions.foreachValue(region ⇒ State.ifActive(region.actorState, context.stop))
    storages.foreachValue(storage ⇒ State.ifActive(storage.actorState, context.stop))
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

    (storage.actorState, region.actorState) match {
      case (State.Active(storageDispatcher), State.Active(regionDispatcher)) ⇒
        storageDispatcher ! StorageIndex.OpenIndex(regionId)
        regionDispatcher ! RegionDispatcher.AttachStorage(storageId, storage.props, storageDispatcher, StorageHealth.empty)

      case _ ⇒
        // Pass
    }
  }

  def unregisterStorage(regionId: String, storageId: String): Unit = {
    require(containsRegionAndStorage(regionId, storageId))
    val region = regions(regionId)
    val storage = storages(storageId)
    regions += regionId → region.copy(storages = region.storages - storageId)
    storages += storageId → storage.copy(regions = storage.regions - regionId)
    State.ifActive(region.actorState, _ ! RegionDispatcher.DetachStorage(storageId))
    State.ifActive(storage.actorState, _ ! StorageIndex.CloseIndex(regionId))
  }

  def registerRegionStorages(regionId: String): Unit = {
    regions.get(regionId) match {
      case Some(region) ⇒
        region.storages.foreach(registerStorage(regionId, _))

      case None ⇒
        // Pass
    }
  }
}
