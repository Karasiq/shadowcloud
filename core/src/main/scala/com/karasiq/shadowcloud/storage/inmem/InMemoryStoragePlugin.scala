package com.karasiq.shadowcloud.storage.inmem

import scala.collection.concurrent.TrieMap
import scala.language.postfixOps

import akka.actor.{ActorContext, ActorRef}
import akka.util.ByteString

import com.karasiq.shadowcloud.model.{Path, StorageId}
import com.karasiq.shadowcloud.storage._
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.PathTreeRepository
import com.karasiq.shadowcloud.storage.utils.StoragePluginBuilder

private[storage] final class InMemoryStoragePlugin extends StoragePlugin {
  def createStorage(storageId: StorageId, props: StorageProps)(implicit context: ActorContext): ActorRef = {
    val indexMap = TrieMap.empty[Path, ByteString]
    val chunkMap = TrieMap.empty[Path, ByteString]

    StoragePluginBuilder(storageId, props)
      .withIndexTree(PathTreeRepository(Repositories.fromConcurrentMap(indexMap)))
      .withChunksTree(PathTreeRepository(Repositories.fromConcurrentMap(chunkMap)))
      .withHealth(StorageHealthProviders.fromMaps(props.quota, indexMap, chunkMap))
      .createStorage()
  }
}
