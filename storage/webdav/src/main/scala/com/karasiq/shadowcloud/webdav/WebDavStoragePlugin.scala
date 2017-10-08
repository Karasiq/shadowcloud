package com.karasiq.shadowcloud.webdav

import akka.actor.ActorContext

import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage.StoragePlugin
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

object WebDavStoragePlugin {
  def apply(): WebDavStoragePlugin = {
    new WebDavStoragePlugin()
  }

  private def dispatcherId = "shadowcloud.storage.webdav.dispatcher"
}

class WebDavStoragePlugin extends StoragePlugin {
  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext) = {
    implicit val dispatcher = context.system.dispatchers.lookup(WebDavStoragePlugin.dispatcherId)
    StoragePluginBuilder(storageId, props)
      .withChunksTree(SardineRepository(props))
      .withIndexTree(SardineRepository(props))
      .withHealth(SardineHealthProvider(props))
      .createStorage()
  }
}
