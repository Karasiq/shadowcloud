package com.karasiq.shadowcloud.model.utils

trait HealthStatus {
  def freeSpace: Long
  def usedSpace: Long
  def totalSpace: Long
  def writableSpace: Long
  def online: Boolean

  def canWrite(bytes: Long): Boolean = {
    online && writableSpace >= bytes
  }
}

object HealthStatus {
  def getUsedPercentage(hs: HealthStatus): Int = {
    val percentage = if (hs.totalSpace > 0) (hs.usedSpace * 100 / hs.totalSpace).toInt else 0
    math.max(0, math.min(100, percentage))
  }
}