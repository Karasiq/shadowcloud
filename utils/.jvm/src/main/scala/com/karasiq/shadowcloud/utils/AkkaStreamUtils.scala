package com.karasiq.shadowcloud.utils

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.{Duration, FiniteDuration}

import akka.NotUsed
import akka.stream.{FlowShape, Graph, SourceShape}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

object AkkaStreamUtils {
  def groupedOrInstant[T, M](queueSize: Int, queueTime: FiniteDuration): Flow[T, Seq[T], NotUsed] = {
    if (queueSize == 0 || queueTime == Duration.Zero) {
      Flow[T].map(Seq(_: T))
    } else {
      Flow[T].groupedWithin(queueSize, queueTime)
    }
  }

  def extractUpstream[T]: Flow[T, Source[T, NotUsed], NotUsed] = {
    Flow[T].prefixAndTail(0).map(_._2)
  }

  def extractUpstreamAndMat[I, O, M](stream: Graph[FlowShape[I, O], M]): Flow[I, (Source[O, NotUsed], Future[M]), NotUsed] = {
    val promise = Promise[M]
    Flow.fromGraph(stream)
      .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
      .mapMaterializedValue { mat ⇒ promise.trySuccess(mat); NotUsed }
      .via(extractUpstream)
      .zip(Source.single(promise.future))
  }

  def flatMapConcatMat[E, E1, M](f: E ⇒ Graph[SourceShape[E1], Future[M]]): Flow[E, E1, Future[Seq[M]]] = {
    Flow[E].map { element ⇒
      val promise = Promise[M]
      val stream = Source.fromGraph(f(element))
        .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
        .mapMaterializedValue { f ⇒ promise.completeWith(f); NotUsed }
      (stream, promise.future)
    }
    .alsoToMat(
      Flow[(Source[E1, NotUsed], Future[M])]
        .mapAsync(1)(_._2)
        .toMat(Sink.seq)(Keep.right)
    )(Keep.right)
    .flatMapConcat(_._1)
  }
}
