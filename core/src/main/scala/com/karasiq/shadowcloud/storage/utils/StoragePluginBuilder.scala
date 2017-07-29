package com.karasiq.shadowcloud.storage.utils

import akka.actor.{ActorContext, ActorRef}
import akka.util.ByteString

import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, StorageDispatcher, StorageIndex}
import com.karasiq.shadowcloud.index.Path
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository._

object StoragePluginBuilder {
  val defaultDelimiter = "__SCD__"

  def defaultIndexPath(props: StorageProps): Path = {
    Path.root / s".sci-${props.address.postfix}"
  }

  def defaultChunksPath(props: StorageProps): Path = {
    Path.root / s".scc-${props.address.postfix}"
  }
}

case class StoragePluginBuilder(storageId: String,
                                props: StorageProps,
                                index: Option[CategorizedRepository[String, Long]] = None,
                                chunks: Option[CategorizedRepository[String, ByteString]] = None,
                                health: Option[StorageHealthProvider] = None) {

  def withIndex(repository: CategorizedRepository[String, Long]): StoragePluginBuilder = {
    copy(index = Some(repository))
  }

  def withIndexTree(repository: PathTreeRepository): StoragePluginBuilder = {
    val indexRepo = PathTreeRepository.toCategorized(repository, StoragePluginBuilder.defaultIndexPath(props))
    withIndex(Repository.forIndex(indexRepo))
  }

  def withIndexKeyValue(repository: KeyValueRepository, delimiter: String = StoragePluginBuilder.defaultDelimiter): StoragePluginBuilder = {
    val pathTreeWrapper = PathTreeRepository.fromKeyValue(repository, delimiter)
    withIndexTree(pathTreeWrapper)
  }

  def withChunks(repository: CategorizedRepository[String, ByteString]): StoragePluginBuilder = {
    copy(chunks = Some(repository))
  }

  def withChunksTree(repository: PathTreeRepository): StoragePluginBuilder = {
    val indexRepo = PathTreeRepository.toCategorized(repository, StoragePluginBuilder.defaultChunksPath(props))
    withChunks(Repository.forChunks(indexRepo))
  }

  def withChunksKeyValue(repository: KeyValueRepository, delimiter: String = StoragePluginBuilder.defaultDelimiter): StoragePluginBuilder = {
    val pathTreeWrapper = PathTreeRepository.fromKeyValue(repository, delimiter)
    withChunksTree(pathTreeWrapper)
  }

  def withHealth(healthProvider: StorageHealthProvider): StoragePluginBuilder = {
    copy(health = Some(healthProvider))
  }

  def createStorage()(implicit context: ActorContext): ActorRef = {
    require(index.nonEmpty, "Index repository not provided")
    require(chunks.nonEmpty, "Chunks repository not provided")
    require(health.nonEmpty, "Health provider not provided")

    val indexSynchronizer = context.actorOf(StorageIndex.props(storageId, props, index.get), "index")
    val chunkIO = context.actorOf(ChunkIODispatcher.props(chunks.get), "chunks")
    context.actorOf(StorageDispatcher.props(storageId, props, indexSynchronizer, chunkIO, health.get), "storageDispatcher")
  }
}
