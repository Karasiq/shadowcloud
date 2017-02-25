package com.karasiq.shadowcloud.actors.internal

import akka.event.LoggingAdapter
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.concurrent.Future
import scala.language.postfixOps

private[actors] object StorageStatsTracker {
  def apply(storageId: String, healthProvider: StorageHealthProvider, log: LoggingAdapter): StorageStatsTracker = {
    new StorageStatsTracker(storageId, healthProvider, log)
  }
}

private[actors] final class StorageStatsTracker(storageId: String, healthProvider: StorageHealthProvider, log: LoggingAdapter) {
  private[this] var health = StorageHealth.empty
  private[this] var stats = DiffStats.empty

  def updateHealth(func: StorageHealth ⇒ StorageHealth): Unit = {
    this.health = func(this.health)
    log.debug("Storage [{}] health updated: {}", storageId, health)
    StorageEvents.stream.publish(StorageEnvelope(storageId, StorageEvents.HealthUpdated(health)))
  }

  def updateStats(stats: DiffStats): Unit = {
    this.stats = stats
    log.debug("Storage [{}] stats updated: {}", storageId, stats)
  }

  def appendStats(stats: DiffStats): Unit = {
    updateStats(this.stats + stats)
  }

  def checkHealth(): Future[StorageHealth] = {
    healthProvider.health
  }
}
