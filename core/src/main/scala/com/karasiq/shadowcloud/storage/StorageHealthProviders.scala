package com.karasiq.shadowcloud.storage

import java.nio.file.Path

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.FileStorageHealthProvider
import com.karasiq.shadowcloud.storage.inmem.InMemoryStorageHealthProvider

import scala.collection.mutable
import scala.language.postfixOps

private[shadowcloud] object StorageHealthProviders {
  def fromDirectory(directory: Path): StorageHealthProvider = {
    new FileStorageHealthProvider(directory)
  }

  def fromMaps(maps: mutable.Map[_, ByteString]*): StorageHealthProvider = {
    new InMemoryStorageHealthProvider(maps)
  }
}
