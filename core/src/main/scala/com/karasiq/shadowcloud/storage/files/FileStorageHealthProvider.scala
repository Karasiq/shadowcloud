package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, Path ⇒ FsPath}

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

private[storage] final class FileStorageHealthProvider(directory: FsPath) extends StorageHealthProvider {
  def health: Future[StorageHealth] = {
    Future.fromTry(Try {
      val fileStore = Files.getFileStore(directory)
      val total = fileStore.getTotalSpace
      val free = fileStore.getUsableSpace
      val canWrite = if (fileStore.isReadOnly) 0 else free
      StorageHealth(canWrite, total, total - free)
    })
  }
}
