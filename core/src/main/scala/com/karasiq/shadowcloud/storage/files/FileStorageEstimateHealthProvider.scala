package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, Path ⇒ FsPath}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

private[storage] final class FileStorageEstimateHealthProvider(directory: FsPath)(implicit ec: ExecutionContext)
  extends StorageHealthProvider {

  def health: Future[StorageHealth] = {
    Future {
      val fileStore = Files.getFileStore(directory)
      val total = fileStore.getTotalSpace
      val free = fileStore.getUsableSpace
      val canWrite = if (fileStore.isReadOnly) 0 else free
      StorageHealth(canWrite, total, total - free)
    }
  }
}
