package com.karasiq.shadowcloud.storage.internal

import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.concurrent.Future
import scala.language.postfixOps

private[storage] final class NoOpStorageHealthProvider extends StorageHealthProvider {
  override val health: Future[StorageHealth] = {
    Future.successful(StorageHealth(Long.MaxValue, Long.MaxValue, 0L))
  }
}
