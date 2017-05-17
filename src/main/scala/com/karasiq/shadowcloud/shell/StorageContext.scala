package com.karasiq.shadowcloud.shell

import scala.language.postfixOps

import com.karasiq.shadowcloud.actors.{GarbageCollector, IndexDispatcher}
import com.karasiq.shadowcloud.actors.RegionSupervisor.DeleteStorage
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope

private[shell] object StorageContext {
  def apply(storageId: String)(implicit context: ShellContext): StorageContext = {
    new StorageContext(storageId)
  }
}

private[shell] final class StorageContext(val storageId: String)(implicit context: ShellContext) {
  import context.sc.actors.regionSupervisor

  def sync(): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, IndexDispatcher.Synchronize)
  }

  def collectGarbage(): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, GarbageCollector.CollectGarbage(force = true))
  }

  def terminate(): Unit ={
    regionSupervisor ! DeleteStorage(storageId)
  }
}
