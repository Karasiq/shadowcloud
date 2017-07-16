package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, Path ⇒ FsPath}

import scala.concurrent.Future
import scala.language.postfixOps
import scala.util.Try

import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.utils.FileSystemUtils

private[storage] final class FileStorageQuotedHealthProvider(quota: StorageProps.Quota,
                                                             directory: FsPath) extends StorageHealthProvider {

  def health: Future[StorageHealth] = { // TODO: Use blocking dispatcher
    Future.fromTry(Try {
      val fileStore = Files.getFileStore(directory)
      val total = quota.getLimitedSpace(fileStore.getTotalSpace)
      val used = FileSystemUtils.getFolderSize(directory)
      val free = math.max(total - used, 0L)
      val canWrite = if (fileStore.isReadOnly) 0 else free
      StorageHealth(canWrite, total, used)
    })
  }
}
