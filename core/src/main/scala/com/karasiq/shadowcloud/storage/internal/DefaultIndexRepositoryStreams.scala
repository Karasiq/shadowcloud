package com.karasiq.shadowcloud.storage.internal

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{FlowShape, SourceShape}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Source, ZipWith}
import akka.util.ByteString

import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.storage.{Repository, StorageIOResult}
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexRepositoryStreams, StorageUtils}

private[storage] final class DefaultIndexRepositoryStreams(breadth: Int, writeFlow: Flow[IndexData, ByteString, _],
                                                           readFlow: Flow[ByteString, IndexData, _])
                                                          (implicit ec: ExecutionContext) extends IndexRepositoryStreams {

  def write[Key](repository: Repository[Key]): Flow[(Key, IndexData), IndexIOResult[Key], NotUsed] = {
    Flow[(Key, IndexData)]
      .flatMapMerge(breadth, { case (key, value) ⇒
        Source.single(value).via(writeAndReturn(repository, key))
      })
  }

  def read[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed] = {
    Flow[Key].flatMapMerge(breadth, readAndReturn(repository, _))
  }

  def delete[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed] = {
    Flow[Key].flatMapMerge(breadth, { key ⇒
      Source.fromFuture(StorageUtils.wrapFuture(repository.toString, repository.delete(key)))
        .map(IndexIOResult(key, IndexData.empty, _))
    })
  }

  private[this] def writeAndReturn[Key](repository: Repository[Key], key: Key): Flow[IndexData, IndexIOResult[Key], NotUsed] = {
    val graph = GraphDSL.create(repository.write(key)) { implicit builder ⇒ repository ⇒
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[IndexData](2, eagerCancel = true))
      val compose = builder.add(ZipWith((diff: IndexData, result: Future[StorageIOResult]) ⇒ (key, diff, result)))
      val unwrap = builder.add(Flow[(Key, IndexData, Future[StorageIOResult])].flatMapConcat { case (key, diff, future) ⇒
        Source.fromFuture(StorageUtils.wrapFuture(repository.toString, future))
          .map(IndexIOResult(key, diff, _))
      })
      broadcast.out(0) ~> compose.in0
      broadcast.out(1) ~> writeFlow ~> repository
      builder.materializedValue ~> compose.in1
      compose.out ~> unwrap
      FlowShape(broadcast.in, unwrap.out)
    }
    Flow.fromGraph(graph).mapMaterializedValue(_ ⇒ NotUsed)
  }

  private[this] def readAndReturn[Key](repository: Repository[Key], key: Key): Source[IndexIOResult[Key], NotUsed] = {
    val graph = GraphDSL.create(repository.read(key)) { implicit builder ⇒ repository ⇒
      import GraphDSL.Implicits._
      val compose = builder.add(ZipWith((diff: IndexData, result: Future[StorageIOResult]) ⇒ (key, diff, result)))
      val unwrap = builder.add(Flow[(Key, IndexData, Future[StorageIOResult])].flatMapConcat { case (key, diff, future) ⇒
        Source.fromFuture(StorageUtils.wrapFuture(repository.toString, future))
          .map(IndexIOResult(key, diff, _))
      })
      repository.out ~> readFlow ~> compose.in0
      builder.materializedValue ~> compose.in1
      compose.out ~> unwrap
      SourceShape(unwrap.out)
    }
    Source.fromGraph(graph).mapMaterializedValue(_ ⇒ NotUsed)
  }
}
