package com.karasiq.shadowcloud.model.utils

import com.karasiq.shadowcloud.model.StorageId

@SerialVersionUID(0L)
final case class RegionHealth(usedSpace: Long, storages: Map[StorageId, StorageHealth]) extends HealthStatus {
  def totalSpace: Long = createSum(_.totalSpace)
  // def usedSpace: Long = createSum(_.usedSpace)
  def freeSpace: Long     = createSum(_.freeSpace)
  def writableSpace: Long = createSum(_.writableSpace)

  def online: Boolean = {
    storages.nonEmpty && storages.values.exists(_.online)
  }

  def fullyOnline: Boolean = {
    storages.nonEmpty && storages.values.forall(_.online)
  }

  def toStorageHealth: StorageHealth = {
    StorageHealth.normalized(writableSpace, totalSpace, usedSpace, online)
  }

  private[this] def createSum(getValue: StorageHealth ⇒ Long): Long = {
    val result = storages.values.filter(_.online).map(h ⇒ BigInt(getValue(h))).sum
    if (!result.isValidLong) Long.MaxValue
    else if (result < 0) 0L
    else result.longValue()
  }
}

object RegionHealth {
  val empty = RegionHealth(0L, Map.empty)
}
