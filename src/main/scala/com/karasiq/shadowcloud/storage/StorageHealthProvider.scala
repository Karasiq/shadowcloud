package com.karasiq.shadowcloud.storage

import java.nio.file.Path

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileStorageHealthProvider
import com.karasiq.shadowcloud.storage.inmem.InMemoryStorageHealthProvider
import com.karasiq.shadowcloud.storage.internal.NoOpStorageHealthProvider

import scala.collection.mutable
import scala.concurrent.Future
import scala.language.postfixOps

case class StorageHealth(canWrite: Long, totalSpace: Long, usedSpace: Long)

trait StorageHealthProvider {
  def health: Future[StorageHealth]
}

object StorageHealthProvider {
  val infinite: StorageHealthProvider = {
    new NoOpStorageHealthProvider
  }

  def fromDirectory(directory: Path): StorageHealthProvider = {
    new FileStorageHealthProvider(directory)
  }

  def fromMaps(maps: Seq[mutable.Map[_, ByteString]]): StorageHealthProvider = {
    new InMemoryStorageHealthProvider(maps)
  }
}