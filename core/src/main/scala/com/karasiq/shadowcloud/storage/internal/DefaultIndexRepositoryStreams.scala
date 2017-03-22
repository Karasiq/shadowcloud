package com.karasiq.shadowcloud.storage.internal

import akka.NotUsed
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Source, ZipWith}
import akka.stream.{FlowShape, SourceShape}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexRepositoryStreams, StorageUtils}
import com.karasiq.shadowcloud.storage.{Repository, StorageIOResult}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

private[storage] final class DefaultIndexRepositoryStreams(breadth: Int, writeFlow: Flow[IndexDiff, ByteString, _],
                                                           readFlow: Flow[ByteString, IndexDiff, _])
                                                          (implicit ec: ExecutionContext) extends IndexRepositoryStreams {

  def write[Key](repository: Repository[Key]): Flow[(Key, IndexDiff), IndexIOResult[Key], NotUsed] = {
    Flow[(Key, IndexDiff)]
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
        .map(IndexIOResult(key, IndexDiff.empty, _))
    })
  }

  private[this] def writeAndReturn[Key](repository: Repository[Key], key: Key): Flow[IndexDiff, IndexIOResult[Key], NotUsed] = {
    val graph = GraphDSL.create(repository.write(key)) { implicit builder ⇒ repository ⇒
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[IndexDiff](2, eagerCancel = true))
      val compose = builder.add(ZipWith((diff: IndexDiff, result: Future[StorageIOResult]) ⇒ (key, diff, result)))
      val unwrap = builder.add(Flow[(Key, IndexDiff, Future[StorageIOResult])].flatMapConcat { case (key, diff, future) ⇒
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
      val compose = builder.add(ZipWith((diff: IndexDiff, result: Future[StorageIOResult]) ⇒ (key, diff, result)))
      val unwrap = builder.add(Flow[(Key, IndexDiff, Future[StorageIOResult])].flatMapConcat { case (key, diff, future) ⇒
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
