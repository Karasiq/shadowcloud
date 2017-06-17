package com.karasiq.shadowcloud.shell

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.language.postfixOps

import com.karasiq.shadowcloud.actors.utils.GCState

private[shell] object StorageContext {
  def apply(storageId: String)(implicit context: ShellContext): StorageContext = {
    new StorageContext(storageId)
  }
}

private[shell] final class StorageContext(val storageId: String)(implicit context: ShellContext) {
  import context.sc.ops.{storage, supervisor}

  def sync(): Unit = {
    storage.synchronize(storageId)
  }

  def collectGarbage(delete: Boolean = false): Map[String, GCState] = {
    val future = storage.collectGarbage(storageId, delete)
    Await.result(future, Duration.Inf)
  }

  def compactIndex(region: String): Unit = {
    storage.compactIndex(storageId, region)
  }

  def terminate(): Unit = {
    supervisor.deleteStorage(storageId)
  }
}
