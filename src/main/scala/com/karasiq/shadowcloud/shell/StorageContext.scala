package com.karasiq.shadowcloud.shell

import com.karasiq.shadowcloud.actors.RegionSupervisor.DeleteStorage

import scala.language.postfixOps

private[shell] object StorageContext {
  def apply(storageId: String)(implicit context: ShellContext): StorageContext = {
    new StorageContext(storageId)
  }
}

private[shell] final class StorageContext(val storageId: String)(implicit context: ShellContext) {
  import context._
  // TODO: Storage-level functions

  def terminate(): Unit ={
    regionSupervisor ! DeleteStorage(storageId)
  }
}
