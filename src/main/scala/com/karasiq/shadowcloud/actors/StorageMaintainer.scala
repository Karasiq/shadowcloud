package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.actors.internal.DiffStats
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.concurrent.duration._
import scala.language.postfixOps

object StorageMaintainer {
  // Messages
  sealed trait Message
  object CheckHealth extends Message with MessageStatus[String, StorageHealth]

  // Events
  sealed trait Event

  // Props
  def props(storageId: String, storageDispatcher: ActorRef, healthProvider: StorageHealthProvider): Props = {
    Props(classOf[StorageMaintainer], storageId, storageDispatcher, healthProvider)
  }
}

class StorageMaintainer(storageId: String, storageDispatcher: ActorRef, healthProvider: StorageHealthProvider) extends Actor with ActorLogging {
  import StorageMaintainer._

  // Context
  import context.dispatcher
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] val schedule = context.system.scheduler.schedule(30 seconds, 30 seconds, self, CheckHealth)

  // State
  var health = StorageHealth.empty
  var stats = DiffStats.empty
  var diffsCount = 0

  def updateHealth(health: StorageHealth): Unit = {
    this.health = health
    log.debug("Storage [{}] health updated: {}", storageId, health)
    StorageEvent.stream.publish(StorageEnvelope(storageId, StorageEvent.HealthUpdated(health)))
  }

  def updateStats(stats: DiffStats): Unit = {
    this.stats = stats
    log.debug("Storage [{}] stats updated: {}", stats)
  }

  override def receive: Receive = {
    // -----------------------------------------------------------------------
    // Storage status
    // -----------------------------------------------------------------------
    case CheckHealth ⇒
      healthProvider.health
        .map(CheckHealth.Success(storageId, _))
        .recover(PartialFunction(CheckHealth.Failure(storageId, _)))
        .pipeTo(self)

    case CheckHealth.Success(`storageId`, health) ⇒
      updateHealth(health)

    case CheckHealth.Failure(`storageId`, error) ⇒
      log.error(error, "Health update failure: {}", storageId)

    // -----------------------------------------------------------------------
    // Storage events
    // -----------------------------------------------------------------------
    case StorageEnvelope(`storageId`, event) ⇒ event match {
      case StorageEvent.IndexLoaded(diffs) ⇒
        diffsCount = diffs.length // TODO: Compact
        updateStats(diffs.foldLeft(DiffStats.empty)((stat, kv) ⇒ stat + DiffStats(kv._2)))

      case StorageEvent.IndexUpdated(_, diff, _) ⇒
        diffsCount += 1
        updateStats(stats + DiffStats(diff))

      case StorageEvent.ChunkWritten(chunk) ⇒
        updateHealth(this.health.copy(canWrite = health.canWrite - chunk.checksum.size,
          usedSpace = health.usedSpace + chunk.checksum.size))
      
      case _ ⇒
        // Ignore
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    StorageEvent.stream.subscribe(self, storageId)
  }

  override def postStop(): Unit = {
    schedule.cancel()
    StorageEvent.stream.unsubscribe(self)
    super.postStop()
  }
}