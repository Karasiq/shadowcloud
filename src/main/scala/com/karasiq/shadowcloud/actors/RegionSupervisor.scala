package com.karasiq.shadowcloud.actors

import akka.actor.{ActorLogging, OneForOneStrategy, PossiblyHarmful, Props, SupervisorStrategy}
import akka.persistence.PersistentActor
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.internal.RegionTracker
import com.karasiq.shadowcloud.actors.internal.RegionTracker.{RegionStatus, StorageStatus}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StorageInstantiator

import scala.concurrent.duration._
import scala.language.postfixOps

object RegionSupervisor {
  // Messages
  sealed trait Message
  case class AddRegion(regionId: String) extends Message
  case class DeleteRegion(regionId: String) extends Message
  case class AddStorage(storageId: String, props: StorageProps) extends Message
  case class DeleteStorage(storageId: String) extends Message
  case class RegisterStorage(regionId: String, storageId: String) extends Message
  case class UnregisterStorage(regionId: String, storageId: String) extends Message
  case object GetState {
    case class Success(regions: Map[String, RegionStatus], storages: Map[String, StorageStatus])
  }

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful

  // Events
  sealed trait Event
  case class RegionAdded(regionId: String) extends Event
  case class RegionDeleted(regionId: String) extends Event
  case class StorageAdded(storageId: String, props: StorageProps) extends Event
  case class StorageDeleted(storageId: String) extends Event
  case class StorageRegistered(regionId: String, storageId: String) extends Event
  case class StorageUnregistered(regionId: String, storageId: String) extends Event

  // Props
  def props(instantiator: StorageInstantiator = StorageInstantiator.default): Props = {
    Props(classOf[RegionSupervisor], instantiator)
  }
}

// TODO: Test
class RegionSupervisor(instantiator: StorageInstantiator) extends PersistentActor with ActorLogging {
  import RegionSupervisor._

  // Context
  private[this] implicit val timeout = Timeout(10 seconds)
  val persistenceId: String = "regions"

  // State
  val state = new RegionTracker()

  def updateState(event: Event): Unit = event match {
    // -----------------------------------------------------------------------
    // Virtual regions
    // -----------------------------------------------------------------------
    case RegionAdded(regionId)⇒
      log.info("Region added: {}", regionId)
      val dispatcher = context.actorOf(RegionDispatcher.props(regionId), regionId)
      state.addRegion(regionId, dispatcher)

    case RegionDeleted(regionId) ⇒
      log.debug("Region deleted: {}", regionId)
      val region = state.deleteRegion(regionId)
      context.stop(region.dispatcher)

    // -----------------------------------------------------------------------
    // Storages
    // -----------------------------------------------------------------------
    case StorageAdded(storageId, props) ⇒
      log.info("Storage added: {} (props = {})", storageId, props)
      val dispatcher = context.actorOf(StorageSupervisor.props(instantiator, storageId, props), storageId)
      state.addStorage(storageId, props, dispatcher)

    case StorageDeleted(storageId) ⇒
      log.info("Storage deleted: {}", storageId)
      val storage = state.deleteStorage(storageId)
      context.stop(storage.dispatcher)

    // -----------------------------------------------------------------------
    // Storage registration
    // -----------------------------------------------------------------------
    case StorageRegistered(regionId, storageId) ⇒
      log.info("Storage {} registered in {}", storageId, regionId)
      state.registerStorage(regionId, storageId)

    case StorageUnregistered(regionId, storageId) ⇒
      log.info("Storage {} unregistered from {}", storageId, regionId)
      state.unregisterStorage(regionId, storageId)

    case _ ⇒
      log.warning("Unhandled event: {}", event)
  }

  def receiveRecover: Receive = { // TODO: Snapshots
    case event: Event ⇒
      updateState(event)
  }

  def receiveCommand: Receive = {
    // -----------------------------------------------------------------------
    // Regions
    // -----------------------------------------------------------------------
    case AddRegion(regionId) if state.containsRegion(regionId) ⇒
      persist(RegionAdded(regionId))(updateState)

    case DeleteRegion(regionId) if state.containsRegion(regionId) ⇒
      persist(RegionDeleted(regionId))(updateState)

    // -----------------------------------------------------------------------
    // Storages
    // -----------------------------------------------------------------------
    case AddStorage(storageId, props) if state.containsStorage(storageId) ⇒
      persist(StorageAdded(storageId, props))(updateState)

    case DeleteStorage(storageId) if state.containsStorage(storageId) ⇒
      persist(StorageDeleted(storageId))(updateState)

    // -----------------------------------------------------------------------
    // Storage registration
    // -----------------------------------------------------------------------
    case RegisterStorage(regionId, storageId) if state.containsRegionAndStorage(regionId, storageId) ⇒
      persist(StorageRegistered(regionId, storageId))(updateState)

    case UnregisterStorage(regionId, storageId) if state.containsRegionAndStorage(regionId, storageId) ⇒
      persist(StorageUnregistered(regionId, storageId))(updateState)

    // -----------------------------------------------------------------------
    // State actions
    // -----------------------------------------------------------------------
    case GetState ⇒
      sender() ! GetState.Success(state.regions.toMap, state.storages.toMap)
  }

  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: IllegalArgumentException ⇒
      SupervisorStrategy.Resume
  }
}
