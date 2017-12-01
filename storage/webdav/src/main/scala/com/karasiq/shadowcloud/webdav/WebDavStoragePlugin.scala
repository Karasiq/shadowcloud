package com.karasiq.shadowcloud.webdav

import akka.actor.{Actor, ActorContext, Props, Terminated}

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
    context.actorOf(Props(new Actor {
      implicit val ec = context.system.dispatchers.lookup(WebDavStoragePlugin.dispatcherId)
      val sardine = SardineRepository.createSardine(props)
      val dispatcher = StoragePluginBuilder(storageId, props)
        .withChunksTree(SardineRepository(props, sardine))
        .withIndexTree(SardineRepository(props, sardine))
        .withHealth(SardineHealthProvider(props, sardine))
        .createStorage()

      context.watch(dispatcher)

      override def postStop(): Unit = {
        sardine.shutdown()
        super.postStop()
      }

      def receive = {
        case Terminated(_) ⇒
          context.stop(self)

        case msg if sender() == dispatcher ⇒
          context.parent ! msg

        case msg ⇒
          dispatcher.forward(msg)
      }
    }))
  }
}
