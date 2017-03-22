package com.karasiq.shadowcloud.actors.internal

import akka.event.LoggingAdapter
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

private[actors] object StorageStatsTracker {
  def apply(storageId: String, healthProvider: StorageHealthProvider, log: LoggingAdapter): StorageStatsTracker = {
    new StorageStatsTracker(storageId, healthProvider, log)
  }
}

private[actors] final class StorageStatsTracker(storageId: String, healthProvider: StorageHealthProvider, log: LoggingAdapter) {
  private[this] var health = StorageHealth.empty
  private[this] var stats = mutable.AnyRefMap.empty[String, DiffStats]
    .withDefaultValue(DiffStats.empty)

  def updateHealth(func: StorageHealth ⇒ StorageHealth): Unit = {
    this.health = func(this.health)
    log.debug("Storage [{}] health updated: {}", storageId, health)
    StorageEvents.stream.publish(StorageEnvelope(storageId, StorageEvents.HealthUpdated(health)))
  }

  def updateStats(region: String, stats: DiffStats): Unit = {
    if (stats.isEmpty) {
      this.stats -= region
      log.debug("{}/{} stats cleared", storageId, region)
    } else {
      this.stats += region → stats
      log.debug("{}/{} stats updated: {}", storageId, region, stats)
    }
  }

  def appendStats(region: String, stats: DiffStats): Unit = {
    updateStats(region, this.stats(region) + stats)
  }

  def clear(region: String): Unit = {
    updateStats(region, DiffStats.empty)
  }

  def requiresCompaction(): Iterable[String] = { // TODO: Config
    this.stats.filter(_._2.deletes > 0).keys
  }

  def checkHealth(): Future[StorageHealth] = {
    healthProvider.health
  }
}
