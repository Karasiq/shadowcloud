package com.karasiq.shadowcloud.storage.inmem

import scala.collection.concurrent.TrieMap
import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRef}
import akka.util.ByteString

import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, IndexDispatcher, StorageDispatcher}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps

private[storage] final class InMemoryStoragePlugin extends StoragePlugin {
  def createStorage(storageId: String, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    val indexMap = TrieMap.empty[(String, String), ByteString]
    val index = Repository.forIndex(Repository.toCategorized(Repositories.fromConcurrentMap(indexMap)))
    val chunkMap = TrieMap.empty[(String, ByteString), ByteString]
    val chunks = Repository.toCategorized(Repositories.fromConcurrentMap(chunkMap))
    val health = StorageHealthProviders.fromMaps(props.quota, indexMap, chunkMap)
    val indexSynchronizer = context.actorOf(IndexDispatcher.props(storageId, index), "index")
    val chunkIO = context.actorOf(ChunkIODispatcher.props(chunks), "chunks")
    context.actorOf(StorageDispatcher.props(storageId, indexSynchronizer, chunkIO, health), "dispatcher")
  }
}
