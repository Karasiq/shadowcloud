package com.karasiq.shadowcloud.storage.files

import java.nio.file.Paths

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.stream.Materializer
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

import scala.concurrent.ExecutionContext

private[storage] object FileStoragePlugin {
  def getBlockingDispatcher(actorSystem: ActorSystem): ExecutionContext = {
    val dispatcherName = actorSystem.settings.config.getString("akka.stream.blocking-io-dispatcher")
    actorSystem.dispatchers.lookup(dispatcherName)
  }
}

private[storage] final class FileStoragePlugin extends StoragePlugin {
  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    implicit val executionContext: ExecutionContext = FileStoragePlugin.getBlockingDispatcher(context.system)
    implicit val materializer: Materializer         = Materializer.matFromSystem(context.system)

    val path = props.address.uri.fold(Paths.get(""))(Paths.get)
    StoragePluginBuilder(storageId, props)
      .withIndexTree(Repositories.fromDirectory(path))
      .withChunksTree(Repositories.fromDirectory(path))
      .withHealth(StorageHealthProviders.fromDirectory(path, props.quota))
      .createStorage()
  }
}
