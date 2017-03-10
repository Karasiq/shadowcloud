package com.karasiq.shadowcloud.storage.inmem

import akka.actor.{ActorContext, ActorRef}
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, IndexDispatcher, StorageDispatcher}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.wrappers.RepositoryWrappers

import scala.collection.concurrent.TrieMap
import scala.language.postfixOps

private[storage] final class InMemoryStoragePlugin extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    val indexMap = TrieMap.empty[(String, String), ByteString]
    val index = RepositoryWrappers.asIndexRepo(RepositoryWrappers.asCategorized(Repository.fromTrieMap(indexMap)))
    val chunkMap = TrieMap.empty[(String, String), ByteString]
    val chunks = RepositoryWrappers.asCategorized(Repository.fromTrieMap(chunkMap))
    val health = StorageHealthProvider.fromMaps(indexMap, chunkMap)
    val indexSynchronizer = context.actorOf(IndexDispatcher.props(storageId, index), "index")
    val chunkIO = context.actorOf(ChunkIODispatcher.props(chunks), "chunks")
    context.actorOf(StorageDispatcher.props(storageId, indexSynchronizer, chunkIO, health), "dispatcher")
  }
}
