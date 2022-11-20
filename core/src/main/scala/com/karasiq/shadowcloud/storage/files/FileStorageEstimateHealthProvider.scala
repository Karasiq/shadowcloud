package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, Path ⇒ FsPath}

import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.storage.StorageHealthProvider

import scala.concurrent.{ExecutionContext, Future}

private[storage] final class FileStorageEstimateHealthProvider(directory: FsPath)(implicit ec: ExecutionContext) extends StorageHealthProvider {

  def health: Future[StorageHealth] = {
    Future {
      if (!Files.isDirectory(directory)) Files.createDirectories(directory)
      val fileStore = Files.getFileStore(directory)
      val total     = fileStore.getTotalSpace
      val free      = fileStore.getUsableSpace
      val canWrite  = if (fileStore.isReadOnly) 0 else free
      StorageHealth(canWrite, total, total - free)
    }
  }
}
