package com.karasiq.shadowcloud.storage.utils

import akka.actor.{ActorContext, ActorRef}
import com.karasiq.shadowcloud.actors.{ChunkIODispatcher, StorageDispatcher, StorageIndex}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository._

object StoragePluginBuilder {
  val defaultDelimiter = "__scd__"

  def getIndexPath(props: StorageProps): Path = {
    getRootPath(props) / "index"
  }

  def getChunksPath(props: StorageProps): Path = {
    getRootPath(props) / "data"
  }

  def getRootPath(props: StorageProps): Path = {
    props.address.path / (".sc-" + props.address.namespace)
  }
}

case class StoragePluginBuilder(storageId: StorageId,
                                props: StorageProps,
                                index: Option[CategorizedRepository[RegionId, SequenceNr]] = None,
                                chunks: Option[CategorizedRepository[RegionId, ChunkId]] = None,
                                health: Option[StorageHealthProvider] = None,
                                lifecycleHook: Option[LifecycleHook] = None) {

  def withIndex(repository: CategorizedRepository[RegionId, SequenceNr]): StoragePluginBuilder = {
    copy(index = Some(repository))
  }

  def withIndexTree(repository: PathTreeRepository): StoragePluginBuilder = {
    val indexRepo = PathTreeRepository.toCategorized(repository, StoragePluginBuilder.getIndexPath(props))
    withIndex(Repository.forIndex(indexRepo))
  }

  def withIndexKeyValue(repository: KeyValueRepository, delimiter: String = StoragePluginBuilder.defaultDelimiter): StoragePluginBuilder = {
    val pathTreeWrapper = PathTreeRepository.fromKeyValue(repository, delimiter)
    withIndexTree(pathTreeWrapper)
  }

  def withChunks(repository: CategorizedRepository[RegionId, ChunkId]): StoragePluginBuilder = {
    copy(chunks = Some(repository))
  }

  def withChunksTree(repository: PathTreeRepository): StoragePluginBuilder = {
    val chunksRepo = PathTreeRepository.toCategorized(repository, StoragePluginBuilder.getChunksPath(props))
    withChunks(Repository.forChunks(chunksRepo))
  }

  def withChunksKeyValue(repository: KeyValueRepository, delimiter: String = StoragePluginBuilder.defaultDelimiter): StoragePluginBuilder = {
    val pathTreeWrapper = PathTreeRepository.fromKeyValue(repository, delimiter)
    withChunksTree(pathTreeWrapper)
  }

  def withHealth(healthProvider: StorageHealthProvider): StoragePluginBuilder = {
    copy(health = Some(healthProvider))
  }

  def withLifecycleHook(lifecycleHook: LifecycleHook): StoragePluginBuilder = {
    copy(lifecycleHook = Some(lifecycleHook))
  }

  def createStorage()(implicit context: ActorContext): ActorRef = {
    import context.dispatcher
    require(index.nonEmpty, "Index repository not provided")
    require(chunks.nonEmpty, "Chunks repository not provided")
    // require(health.nonEmpty, "Health provider not provided")

    val indexSynchronizer = context.actorOf(StorageIndex.props(storageId, props, index.get), "index")
    val chunkIO = context.actorOf(ChunkIODispatcher.props(storageId, props, chunks.get), "chunks")
    val healthProvider = StorageHealthProvider.applyQuota(health.getOrElse(StorageHealthProvider.unlimited), props.quota)
    val storageDispatcher = context.actorOf(StorageDispatcher.props(storageId, props, indexSynchronizer, chunkIO, healthProvider, lifecycleHook), "storageDispatcher")
    context.watch(indexSynchronizer)
    context.watch(chunkIO)
    context.watch(storageDispatcher)
    storageDispatcher
  }
}
