package com.karasiq.shadowcloud.storage

import java.nio.file.Path

import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.language.postfixOps

import akka.util.ByteString

import com.karasiq.shadowcloud.storage.files.{FileStorageEstimateHealthProvider, FileStorageQuotedHealthProvider}
import com.karasiq.shadowcloud.storage.inmem.InMemoryStorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps

private[shadowcloud] object StorageHealthProviders {
  def fromDirectory(directory: Path, quota: StorageProps.Quota = StorageProps.Quota.empty)
                   (implicit ec: ExecutionContext): StorageHealthProvider = {

    if (quota.isEmpty) {
      // Quickly checks drive free space
      new FileStorageEstimateHealthProvider(directory)
    } else {
      // Counts every file size in directory
      new FileStorageQuotedHealthProvider(quota, directory)
    }
  }

  def fromMaps(quota: StorageProps.Quota, maps: mutable.Map[_, ByteString]*): StorageHealthProvider = {
    new InMemoryStorageHealthProvider(maps, quota)
  }

  def fromMaps(maps: mutable.Map[_, ByteString]*): StorageHealthProvider = {
    fromMaps(StorageProps.Quota.empty, maps:_*)
  }
}
