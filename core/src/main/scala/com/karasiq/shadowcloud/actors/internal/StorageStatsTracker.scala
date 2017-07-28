package com.karasiq.shadowcloud.actors.internal

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

import akka.actor.ActorContext
import akka.event.Logging

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

private[actors] object StorageStatsTracker {
  def apply(storageId: String, config: StorageConfig,
            healthProvider: StorageHealthProvider)
           (implicit context: ActorContext): StorageStatsTracker = {
    new StorageStatsTracker(storageId, config, healthProvider)
  }
}

private[actors] final class StorageStatsTracker(storageId: String, config: StorageConfig,
                                                healthProvider: StorageHealthProvider)
                                               (implicit context: ActorContext) {

  private[this] val log = Logging(context.system, context.self)
  private[this] val sc = ShadowCloud()
  private[this] var health = StorageHealth.empty
  private[this] var stats = mutable.AnyRefMap.empty[String, DiffStats]
    .withDefaultValue(DiffStats.empty)

  def updateHealth(func: StorageHealth ⇒ StorageHealth): Unit = {
    this.health = func(this.health)
    log.debug("Storage [{}] health updated: {}", storageId, health)
    sc.eventStreams.publishStorageEvent(storageId, StorageEvents.HealthUpdated(health))
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

  def requiresCompaction(): Iterable[String] = {
    if (config.indexCompactThreshold <= 0) return Nil
    this.stats.filter(_._2.deletes > config.indexCompactThreshold).keys
  }

  def checkHealth(): Future[StorageHealth] = {
    healthProvider.health
  }
}
