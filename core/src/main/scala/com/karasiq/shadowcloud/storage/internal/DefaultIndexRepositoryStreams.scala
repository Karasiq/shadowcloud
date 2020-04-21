package com.karasiq.shadowcloud.storage.internal

import akka.NotUsed
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Source, ZipWith}
import akka.stream.{FlowShape, SourceShape}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.Repository
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexRepositoryStreams, StorageUtils}

import scala.concurrent.{ExecutionContext, Future}

private[storage] final class DefaultIndexRepositoryStreams(breadth: Int, writeFlow: Flow[IndexData, ByteString, _],
                                                           readFlow: Flow[ByteString, IndexData, _])
                                                          (implicit ec: ExecutionContext) extends IndexRepositoryStreams {

  def write[Key](repository: Repository[Key]): Flow[(Key, IndexData), IndexIOResult[Key], NotUsed] = {
    Flow[(Key, IndexData)].flatMapMerge(breadth, { case (key, value) ⇒
      Source.single(value).via(writeAndReturn(repository, key))
    })
  }

  def read[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed] = {
    Flow[Key].flatMapMerge(breadth, readAndReturn(repository, _))
  }

  def delete[Key](repository: Repository[Key]): Flow[Key, IndexIOResult[Key], NotUsed] = {
    Flow[Key].flatMapMerge(breadth, { key ⇒
      val graph = GraphDSL.create(repository.delete) { implicit builder ⇒ deleteSink ⇒
        import GraphDSL.Implicits._
        val broadcast = builder.add(Broadcast[Key](2))
        val zipKeyAndResult = builder.add(ZipWith((key: Key, result: StorageIOResult) ⇒ IndexIOResult(key, IndexData.empty, result)))
        broadcast ~> deleteSink
        broadcast ~> zipKeyAndResult.in0
        builder.materializedValue.flatMapConcat(Source.fromFuture) ~> zipKeyAndResult.in1
        FlowShape(broadcast.in, zipKeyAndResult.out)
      }
      Source.single(key).via(graph)
    })
  }

  private[this] def writeAndReturn[Key](repository: Repository[Key], key: Key): Flow[IndexData, IndexIOResult[Key], NotUsed] = {
    val graph = GraphDSL.create(repository.write(key)) { implicit builder ⇒ repository ⇒
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[IndexData](2))
      val compose = builder.add(ZipWith((diff: IndexData, result: Future[StorageIOResult]) ⇒ (key, diff, result)))
      val unwrap = builder.add(Flow[(Key, IndexData, Future[StorageIOResult])].flatMapConcat { case (key, diff, future) ⇒
        Source.fromFuture(StorageUtils.wrapFuture(repository.toString, future))
          .map(IndexIOResult(key, diff, _))
      })
      broadcast ~> compose.in0
      broadcast ~> writeFlow ~> repository
      builder.materializedValue ~> compose.in1
      compose.out ~> unwrap
      FlowShape(broadcast.in, unwrap.out)
    }

    Flow[IndexData]
      .take(1)
      .via(graph)
      .take(1)
      .mapMaterializedValue(_ ⇒ NotUsed)
      .named("writeAndReturn")
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

    Source.fromGraph(graph)
      .take(1)
      .mapMaterializedValue(_ ⇒ NotUsed)
      .named("readAndReturn")
  }
}
