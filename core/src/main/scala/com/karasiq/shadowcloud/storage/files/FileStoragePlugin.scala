package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, Paths}

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRef}
import akka.stream.{ActorMaterializer, Materializer}

import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, StorageDispatcher, StorageIndex}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps

private[storage] final class FileStoragePlugin extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    implicit val executionContext: ExecutionContext = {
      val dispatcherName = context.system.settings.config.getString("akka.stream.blocking-io-dispatcher")
      context.system.dispatchers.lookup(dispatcherName)
    }

    implicit val materializer: Materializer = ActorMaterializer()
    
    val path = Paths.get(props.address.uri)
    val indexDir = path.resolve(s".scli-${props.address.postfix}")
    Files.createDirectories(indexDir)
    val index = Repository.forIndex(PathTreeRepository.toCategorized(Repositories.fromDirectory(indexDir)))
    val chunksDir = path.resolve(s".sclc-${props.address.postfix}")
    Files.createDirectories(chunksDir)
    val chunks = Repository.forChunks(PathTreeRepository.toCategorized(Repositories.fromDirectory(chunksDir)))
    val health = StorageHealthProviders.fromDirectory(path, props.quota)
    val indexSynchronizer = context.actorOf(StorageIndex.props(storageId, props, index), "index")
    val chunkIO = context.actorOf(ChunkIODispatcher.props(chunks), "chunks")
    context.actorOf(StorageDispatcher.props(storageId, props, indexSynchronizer, chunkIO, health), "dispatcher")
  }
}
