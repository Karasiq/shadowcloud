package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, Path => FsPath}

import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.concurrent.Future
import scala.language.postfixOps

private[storage] final class FileStorageHealthProvider(directory: FsPath) extends StorageHealthProvider {
  private[this] val fileStore = Files.getFileStore(directory)

  def health: Future[StorageHealth] = {
    val total = fileStore.getTotalSpace
    val free = fileStore.getUsableSpace
    Future.successful(StorageHealth(free, total, total - free))
  }
}
