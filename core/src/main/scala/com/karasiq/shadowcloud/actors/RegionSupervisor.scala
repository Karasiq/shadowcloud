package com.karasiq.shadowcloud.actors

import akka.actor.{ActorLogging, OneForOneStrategy, PossiblyHarmful, Props, SupervisorStrategy}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.internal.RegionTracker.{RegionStatus, StorageStatus}
import com.karasiq.shadowcloud.actors.internal.{RegionTracker, StorageInstantiator}
import com.karasiq.shadowcloud.actors.messages.{RegionEnvelope, StorageEnvelope}
import com.karasiq.shadowcloud.providers.ModuleRegistry
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.collection.mutable
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

  // Snapshot
  private case class Snapshot(regions: Map[String, Set[String]], storages: Map[String, StorageProps])

  // Props
  def props: Props = {
    Props(classOf[RegionSupervisor])
  }
}

class RegionSupervisor extends PersistentActor with ActorLogging with RegionSupervisorState {
  import RegionSupervisor._

  // -----------------------------------------------------------------------
  // Settings
  // -----------------------------------------------------------------------
  private[this] implicit val timeout = Timeout(10 seconds)
  val persistenceId: String = "regions"
  val instantiator = StorageInstantiator(ModuleRegistry())

  // -----------------------------------------------------------------------
  // Recover
  // -----------------------------------------------------------------------
  def receiveRecover: Receive = { // TODO: Snapshots
    val storages = mutable.AnyRefMap.empty[String, StorageProps]
    val regions = mutable.AnyRefMap.empty[String, Set[String]]
    val recoverFunc: Receive = {
      case SnapshotOffer(_, snapshot: Snapshot) ⇒
        regions.clear()
        regions ++= snapshot.regions
        storages.clear()
        storages ++= snapshot.storages

      case RegionAdded(regionId) if !regions.contains(regionId) ⇒
        regions += regionId → Set.empty

      case RegionDeleted(regionId) ⇒
        regions -= regionId

      case StorageAdded(storageId, props) ⇒
        storages += storageId → props

      case StorageDeleted(storageId) ⇒
        storages -= storageId

      case StorageRegistered(regionId, storageId) if regions.contains(regionId) && storages.contains(storageId) ⇒
        regions += regionId → (regions(regionId) + storageId)

      case StorageUnregistered(regionId, storageId) if regions.contains(regionId) && storages.contains(storageId) ⇒
        regions += regionId → (regions(regionId) - storageId)

      case RecoveryCompleted ⇒
        if (log.isDebugEnabled) log.debug("Recovery completed: {} storages, {} regions", storages.size, regions.size)
        loadState(storages, regions)

      case event ⇒
        log.warning("Event unhandled: {}", event)
    }
    recoverFunc
  }

  // -----------------------------------------------------------------------
  // Commands
  // -----------------------------------------------------------------------
  def receiveCommand: Receive = {
    // -----------------------------------------------------------------------
    // Regions
    // -----------------------------------------------------------------------
    case AddRegion(regionId) if !state.containsRegion(regionId) ⇒
      persist(RegionAdded(regionId))(updateState)

    case DeleteRegion(regionId) if state.containsRegion(regionId) ⇒
      persist(RegionDeleted(regionId))(updateState)

    // -----------------------------------------------------------------------
    // Storages
    // -----------------------------------------------------------------------
    case AddStorage(storageId, props) if !state.containsStorage(storageId) ⇒
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

    // -----------------------------------------------------------------------
    // Envelopes
    // -----------------------------------------------------------------------
    case RegionEnvelope(regionId, message) if state.containsRegion(regionId) ⇒
      state.regions(regionId).dispatcher.forward(message)

    case StorageEnvelope(storageId, message) if state.containsStorage(storageId) ⇒
      state.storages(storageId).dispatcher.forward(message)
  }

  // -----------------------------------------------------------------------
  // Supervisor strategy
  // -----------------------------------------------------------------------
  override def supervisorStrategy: SupervisorStrategy = OneForOneStrategy() {
    case _: IllegalArgumentException ⇒
      SupervisorStrategy.Resume
  }
}

sealed trait RegionSupervisorState { self: RegionSupervisor ⇒
  import RegionSupervisor._
  val state = RegionTracker()

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
      state.deleteRegion(regionId)

    // -----------------------------------------------------------------------
    // Storages
    // -----------------------------------------------------------------------
    case StorageAdded(storageId, props) ⇒
      log.info("Storage added: {} (props = {})", storageId, props)
      val dispatcher = context.actorOf(StorageSupervisor.props(instantiator, storageId, props), storageId)
      state.addStorage(storageId, props, dispatcher)

    case StorageDeleted(storageId) ⇒
      log.info("Storage deleted: {}", storageId)
      state.deleteStorage(storageId)

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

  def loadState(storages: collection.Map[String, StorageProps], regions: collection.Map[String, Set[String]]): Unit = {
    state.clear()
    storages.map(StorageAdded.tupled).foreach(updateState)
    regions.foreach { case (regionId, storages) ⇒
      updateState(RegionAdded(regionId))
      storages.foreach(storageId ⇒ updateState(StorageRegistered(regionId, storageId)))
    }
  }
}
