package com.karasiq.shadowcloud.webdav

import akka.actor.{Actor, ActorContext, Props, Terminated}
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.providers.LifecycleHook
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
    implicit val ec                   = context.system.dispatchers.lookup(WebDavStoragePlugin.dispatcherId)
    val sardine                       = SardineRepository.createSardine(props)
    val repository: SardineRepository = SardineRepository(props, sardine)
    StoragePluginBuilder(storageId, props)
      .withChunksTree(repository)
      .withIndexTree(repository)
      .withHealth(SardineHealthProvider(props, sardine))
      .withLifecycleHook(LifecycleHook.shutdown(sardine.shutdown()))
      .createStorage()
  }
}
