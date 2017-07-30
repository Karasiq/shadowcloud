package com.karasiq.shadowcloud.shell

import scala.language.postfixOps

private[shell] object StorageContext {
  def apply(storageId: String)(implicit context: ShellContext): StorageContext = {
    new StorageContext(storageId)
  }
}

private[shell] final class StorageContext(val storageId: String)(implicit context: ShellContext) {
  import context.sc.ops.{storage, supervisor}

  def sync(regionId: String): Unit = {
    storage.synchronize(storageId, regionId)
  }

  def compactIndex(regionId: String): Unit = {
    storage.compactIndex(storageId, regionId)
  }

  def terminate(): Unit = {
    supervisor.deleteStorage(storageId)
  }
}
