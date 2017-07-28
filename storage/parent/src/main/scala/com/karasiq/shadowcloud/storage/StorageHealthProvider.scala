package com.karasiq.shadowcloud.storage

import scala.concurrent.Future
import scala.language.postfixOps

import com.karasiq.shadowcloud.utils.MemorySize

case class StorageHealth(canWrite: Long, totalSpace: Long, usedSpace: Long, online: Boolean = true) {
  require(canWrite >= 0 && totalSpace >= 0 && usedSpace >= 0, "Invalid sizes")

  def freeSpace: Long = {
    math.max(0L, totalSpace - usedSpace)
  }

  def -(bytes: Long): StorageHealth = {
    val newUsedSpace = usedSpace + bytes
    copy(canWrite = math.max(0L, canWrite - bytes), usedSpace = if (newUsedSpace >= 0) newUsedSpace else Long.MaxValue)
  }

  override def toString: String = {
    s"StorageHealth(${if (online) "Online" else "Offline"}, ${MemorySize.toString(canWrite)} available, ${MemorySize.toString(usedSpace)}/${MemorySize.toString(totalSpace)})"
  }
}

object StorageHealth {
  val empty = StorageHealth(0, 0, 0)
  val unlimited = StorageHealth(Long.MaxValue, Long.MaxValue, 0)
}

trait StorageHealthProvider {
  def health: Future[StorageHealth]
}

object StorageHealthProvider {
  val unlimited: StorageHealthProvider = new StorageHealthProvider {
    override def health: Future[StorageHealth] = Future.successful(StorageHealth.unlimited)
  }
}