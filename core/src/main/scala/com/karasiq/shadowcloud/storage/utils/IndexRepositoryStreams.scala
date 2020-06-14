package com.karasiq.shadowcloud.storage.utils

import akka.NotUsed
import akka.actor.{ActorContext, ActorSystem}
import akka.stream.scaladsl.Flow
import akka.util.ByteString
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.storage.internal.DefaultIndexRepositoryStreams
import com.karasiq.shadowcloud.storage.repository.Repository
import com.karasiq.shadowcloud.streams.index.IndexProcessingStreams

import scala.concurrent.ExecutionContext

private[shadowcloud] trait IndexRepositoryStreams {
  def write[Key](repository: Repository[Key]): Flow[(Key, IndexData), IndexIOResult[Key], NotUsed]
  def read[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed]
  def delete[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed]
}

private[shadowcloud] object IndexRepositoryStreams {
  def create(breadth: Int, writeFlow: Flow[IndexData, ByteString, _],
             readFlow: Flow[ByteString, IndexData, _], immutable: Boolean = false)(implicit ec: ExecutionContext): IndexRepositoryStreams = {
    new DefaultIndexRepositoryStreams(breadth, writeFlow, readFlow, immutable)
  }

  def apply(regionId: RegionId, storageConfig: StorageConfig, actorSystem: ActorSystem): IndexRepositoryStreams = {
    import actorSystem.dispatcher
    implicit val sc = ShadowCloud(actorSystem)
    val index = IndexProcessingStreams(regionId)
    create(3, index.preWrite(storageConfig), index.postRead, storageConfig.immutable)
  }

  def apply(regionId: RegionId, storageConfig: StorageConfig)(implicit ac: ActorContext): IndexRepositoryStreams = {
    apply(regionId, storageConfig, ac.system)
  }
}
