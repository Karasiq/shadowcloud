package com.karasiq.shadowcloud.storage.internal

import akka.NotUsed
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Source}
import akka.stream.{FlowShape, IOResult}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{IndexRepository, IndexRepositoryStreams}

import scala.concurrent.Future
import scala.language.postfixOps

private[storage] final class DefaultIndexRepositoryStreams(breadth: Int, writeFlow: Flow[IndexDiff, ByteString, _], readFlow: Flow[ByteString, IndexDiff, _]) extends IndexRepositoryStreams {
  private[this] def writeAndReturn[Key](repository: IndexRepository[Key], key: Key): Flow[IndexDiff, (Key, IndexDiff), Future[IOResult]] = {
    Flow.fromGraph(GraphDSL.create(repository.write(key)) { implicit builder ⇒ repository ⇒
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[IndexDiff](2, eagerCancel = true))
      val result = builder.add(Flow[IndexDiff].map((key, _)))
      broadcast.out(0) ~> writeFlow ~> repository
      broadcast.out(1) ~> result
      FlowShape(broadcast.in, result.out)
    })
  }

  def write[Key](repository: IndexRepository[Key]): Flow[(Key, IndexDiff), (Key, IndexDiff), NotUsed] = {
    Flow[(Key, IndexDiff)]
      .flatMapMerge(breadth, { case (key, value) ⇒
        Source.single(value).via(writeAndReturn(repository, key))
      })
  }

  def read[Key](repository: IndexRepository[Key]): Flow[Key, (Key, IndexDiff), NotUsed] = {
    Flow[Key]
      .flatMapMerge(breadth, key ⇒ repository.read(key).via(readFlow).map((key, _)))
  }
}
