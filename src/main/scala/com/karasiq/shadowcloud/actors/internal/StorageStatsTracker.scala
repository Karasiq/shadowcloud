package com.karasiq.shadowcloud.actors.internal

import akka.event.LoggingAdapter
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.concurrent.Future
import scala.language.postfixOps

private[actors] final class StorageStatsTracker(storageId: String, healthProvider: StorageHealthProvider, log: LoggingAdapter) {
  private[this] var health = StorageHealth.empty
  private[this] var stats = DiffStats.empty

  def updateHealth(func: StorageHealth ⇒ StorageHealth): Unit = {
    this.health = func(this.health)
    log.debug("Storage [{}] health updated: {}", storageId, health)
    StorageEvent.stream.publish(StorageEnvelope(storageId, StorageEvent.HealthUpdated(health)))
  }

  def updateStats(stats: DiffStats): Unit = {
    this.stats = stats
    log.debug("Storage [{}] stats updated: {}", stats)
  }

  def addStats(stats: DiffStats): Unit = {
    updateStats(this.stats + stats)
  }

  def checkHealth(): Future[StorageHealth] = {
    healthProvider.health
  }
}
