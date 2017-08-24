package com.karasiq.shadowcloud.storage.files

import java.nio.file.Paths

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRef, ActorSystem}
import akka.stream.{ActorMaterializer, Materializer}

import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

private[storage] object FileStoragePlugin {
  def getBlockingDispatcher(actorSystem: ActorSystem): ExecutionContext = {
    val dispatcherName = actorSystem.settings.config.getString("akka.stream.blocking-io-dispatcher")
    actorSystem.dispatchers.lookup(dispatcherName)
  }
}

private[storage] final class FileStoragePlugin extends StoragePlugin {
  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    implicit val executionContext: ExecutionContext = FileStoragePlugin.getBlockingDispatcher(context.system)
    implicit val materializer: Materializer = ActorMaterializer()
    
    val path = Paths.get(props.address.uri)
    StoragePluginBuilder(storageId, props)
      .withIndexTree(Repositories.fromDirectory(path))
      .withChunksTree(Repositories.fromDirectory(path))
      .withHealth(StorageHealthProviders.fromDirectory(path, props.quota))
      .createStorage()
  }
}
