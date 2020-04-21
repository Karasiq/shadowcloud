package com.karasiq.shadowcloud.shell

import com.karasiq.shadowcloud.model.utils.SyncReport
import com.karasiq.shadowcloud.model.{RegionId, StorageId}

import scala.concurrent.Await
import scala.concurrent.duration.Duration

private[shell] object StorageContext {
  def apply(storageId: StorageId)(implicit context: ShellContext): StorageContext = {
    new StorageContext(storageId)
  }
}

private[shell] final class StorageContext(val storageId: StorageId)(implicit context: ShellContext) {
  import context.sc.ops.{storage, supervisor}

  def sync(regionId: RegionId): SyncReport = {
    Await.result(storage.synchronize(storageId, regionId), Duration.Inf)
  }

  def compactIndex(regionId: RegionId): Unit = {
    storage.compactIndex(storageId, regionId)
  }

  def terminate(): Unit = {
    supervisor.deleteStorage(storageId)
  }
}
