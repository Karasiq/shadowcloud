package com.karasiq.shadowcloud.storage

import java.nio.file.Path

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileStorageHealthProvider
import com.karasiq.shadowcloud.storage.inmem.InMemoryStorageHealthProvider
import com.karasiq.shadowcloud.storage.internal.NoOpStorageHealthProvider
import com.karasiq.shadowcloud.utils.MemorySize

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

case class StorageHealth(canWrite: Long, totalSpace: Long, usedSpace: Long) {
  require(canWrite >= 0 && totalSpace >= 0 && usedSpace >= 0)

  def -(bytes: Long): StorageHealth = {
    val newUsedSpace = usedSpace + bytes
    copy(canWrite = math.max(0L, canWrite - bytes), usedSpace = if (newUsedSpace >= 0) newUsedSpace else Long.MaxValue)
  }

  override def toString: String = {
    f"StorageHealth(${MemorySize.toString(canWrite)} available, ${MemorySize.toString(usedSpace)}/${MemorySize.toString(totalSpace)})"
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
  val unlimited: StorageHealthProvider = {
    new NoOpStorageHealthProvider
  }

  def fromDirectory(directory: Path): StorageHealthProvider = {
    new FileStorageHealthProvider(directory)
  }

  def fromMaps(maps: mutable.Map[_, ByteString]*): StorageHealthProvider = {
    new InMemoryStorageHealthProvider(maps)
  }
}