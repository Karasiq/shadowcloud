package com.karasiq.shadowcloud.storage

import java.nio.file.Path

import akka.util.ByteString
import com.karasiq.shadowcloud.storage.files.{FileStorageEstimateHealthProvider, FileStorageTraverseHealthProvider}
import com.karasiq.shadowcloud.storage.inmem.JVMHeapHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.collection.mutable
import scala.concurrent.ExecutionContext

private[shadowcloud] object StorageHealthProviders {
  def fromDirectory(directory: Path, quota: StorageProps.Quota = StorageProps.Quota.empty)
                   (implicit ec: ExecutionContext): StorageHealthProvider = {

    if (quota.isEmpty) {
      // Quickly checks drive free space
      new FileStorageEstimateHealthProvider(directory)
    } else {
      // Counts every file size in directory
      StorageHealthProvider.applyQuota(new FileStorageTraverseHealthProvider(directory), quota)
    }
  }

  def fromMaps(quota: StorageProps.Quota, maps: mutable.Map[_, ByteString]*): StorageHealthProvider = {
    new JVMHeapHealthProvider(() ⇒ maps.iterator.flatMap(_.valuesIterator))
  }

  def fromMaps(maps: mutable.Map[_, ByteString]*): StorageHealthProvider = {
    fromMaps(StorageProps.Quota.empty, maps:_*)
  }
}
