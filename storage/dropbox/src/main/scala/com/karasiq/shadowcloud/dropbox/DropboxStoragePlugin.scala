package com.karasiq.shadowcloud.dropbox

import akka.actor.ActorContext

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps

object DropboxStoragePlugin {
  def apply(): DropboxStoragePlugin = {
    new DropboxStoragePlugin()
  }
}

class DropboxStoragePlugin extends StoragePlugin {
  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext) = {
    implicit val sc = ShadowCloud()
    context.actorOf(DropboxSessionProxy.props(storageId, props), "dropbox-session")
  }
}
