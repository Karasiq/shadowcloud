package com.karasiq.shadowcloud.storage.files

import java.nio.file.{Files, Paths}

import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRef}

import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, IndexDispatcher, StorageDispatcher}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps

private[storage] final class FileStoragePlugin extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    import context.dispatcher
    val path = Paths.get(props.address.uri)
    val indexDir = path.resolve(s".scli-${props.address.postfix}")
    Files.createDirectories(indexDir)
    val index = Repository.forIndex(Repositories.fromDirectory(indexDir))
    val chunksDir = path.resolve(s".sclc-${props.address.postfix}")
    Files.createDirectories(chunksDir)
    val chunks = Repository.forChunks(Repositories.fromDirectory(chunksDir))
    val health = StorageHealthProviders.fromDirectory(path, props.quota)
    val indexSynchronizer = context.actorOf(IndexDispatcher.props(storageId, index), "index")
    val chunkIO = context.actorOf(ChunkIODispatcher.props(chunks), "chunks")
    context.actorOf(StorageDispatcher.props(storageId, indexSynchronizer, chunkIO, health), "dispatcher")
  }
}
