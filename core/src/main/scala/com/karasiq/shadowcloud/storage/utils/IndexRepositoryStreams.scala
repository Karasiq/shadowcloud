package com.karasiq.shadowcloud.storage.utils

import scala.concurrent.ExecutionContext
import scala.language.postfixOps

import akka.NotUsed
import akka.actor.{ActorContext, ActorSystem}
import akka.stream.scaladsl.Flow
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.storage.Repository
import com.karasiq.shadowcloud.storage.internal.DefaultIndexRepositoryStreams

private[shadowcloud] trait IndexRepositoryStreams {
  def write[Key](repository: Repository[Key]): Flow[(Key, IndexData), IndexIOResult[Key], NotUsed]
  def read[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed]
  def delete[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed]
}

private[shadowcloud] object IndexRepositoryStreams {
  def create(breadth: Int, writeFlow: Flow[IndexData, ByteString, _],
             readFlow: Flow[ByteString, IndexData, _])(implicit ec: ExecutionContext): IndexRepositoryStreams = {
    new DefaultIndexRepositoryStreams(breadth, writeFlow, readFlow)
  }

  def apply(storageConfig: StorageConfig, actorSystem: ActorSystem): IndexRepositoryStreams = {
    import actorSystem.dispatcher
    val sc = ShadowCloud(actorSystem)
    create(3, sc.streams.index.preWrite(storageConfig), sc.streams.index.postRead)
  }

  def apply(storageConfig: StorageConfig)(implicit ac: ActorContext): IndexRepositoryStreams = {
    apply(storageConfig, ac.system)
  }
}
