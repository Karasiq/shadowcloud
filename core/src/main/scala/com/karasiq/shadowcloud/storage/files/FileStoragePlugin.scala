package com.karasiq.shadowcloud.storage.files

import java.nio.file.Paths

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, IndexSynchronizer, StorageDispatcher}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps

import scala.language.postfixOps

private[storage] final class FileStoragePlugin extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    import context.dispatcher
    val path = Paths.get(props.address.address)
    val index = IndexRepository.fromDirectory(path.resolve(s".scli-${props.address.postfix}"))
    val chunks = ChunkRepository.fromDirectory(path.resolve(s".sclc-${props.address.postfix}"))
    val health = StorageHealthProvider.fromDirectory(path)
    val indexSynchronizer = context.actorOf(IndexSynchronizer.props(storageId, index), "index")
    val chunkIO = context.actorOf(ChunkIODispatcher.props(chunks), "chunks")
    context.actorOf(StorageDispatcher.props(storageId, indexSynchronizer, chunkIO, health), "dispatcher")
  }
}
