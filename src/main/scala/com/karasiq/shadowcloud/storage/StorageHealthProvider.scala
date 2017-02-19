package com.karasiq.shadowcloud.storage

import java.nio.file.Path

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileStorageHealthProvider
import com.karasiq.shadowcloud.storage.inmem.InMemoryStorageHealthProvider
import com.karasiq.shadowcloud.storage.internal.NoOpStorageHealthProvider
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

case class StorageHealth(canWrite: Long, totalSpace: Long, usedSpace: Long) {
  override def toString: String = {
    f"StorageHealth(${Utils.printSize(canWrite)} available, ${Utils.printSize(usedSpace)}/${Utils.printSize(totalSpace)})"
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