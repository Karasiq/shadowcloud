package com.karasiq.shadowcloud.model.utils

import com.karasiq.shadowcloud.utils.Utils

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
    val percentage = Utils.percents(hs.usedSpace, hs.totalSpace).toInt
    math.max(0, math.min(100, percentage))
  }
}