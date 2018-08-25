package com.karasiq.shadowcloud.streams.utils

import java.io.InputStream

import scala.concurrent.{Future, Promise}
import scala.concurrent.duration.{Duration, FiniteDuration, _}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.stream.{FlowShape, Graph, SourceShape}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, StreamConverters}
import akka.util.ByteString

object AkkaStreamUtils {
  def groupedOrInstant[T, M](queueSize: Int, queueTime: FiniteDuration): Flow[T, Seq[T], NotUsed] = {
    if (queueSize == 0 || queueTime == Duration.Zero) {
      Flow[T].map(Seq(_: T))
    } else {
      Flow[T].groupedWithin(queueSize, queueTime)
    }
  }

  def extractUpstream[T]: Flow[T, Source[T, NotUsed], NotUsed] = {
    Flow[T].prefixAndTail(0).map(_._2).named("extractUpstream")
  }

  def dropUpstream[T, T1, M](newStream: Source[T1, M]): Flow[T, T1, M] = {
    Flow.fromSinkAndSourceMat(Sink.cancelled, newStream)(Keep.right).named("dropUpstream")
  }

  def extractUpstreamAndMat[I, O, M](stream: Graph[FlowShape[I, O], M]): Flow[I, (Source[O, NotUsed], Future[M]), NotUsed] = {
    val promise = Promise[M]
    Flow.fromGraph(stream)
      .alsoTo(failPromiseOnComplete(promise))
      .mapMaterializedValue { mat ⇒ promise.trySuccess(mat); NotUsed }
      .via(extractUpstream)
      .zip(Source.single(promise.future))
      .named("extractUpstreamAndMat")
  }

  def flatMapConcatMat[E, E1, M](f: E ⇒ Graph[SourceShape[E1], Future[M]]): Flow[E, E1, Future[Seq[M]]] = {
    Flow[E].map { element ⇒
      val promise = Promise[M]
      val stream = Source.fromGraph(f(element))
        .alsoTo(failPromiseOnFailure(promise))
        .mapMaterializedValue { f ⇒ promise.completeWith(f); NotUsed }
      (stream, promise.future)
    }
    .alsoToMat(
      Flow[(Source[E1, NotUsed], Future[M])]
        .mapAsync(1)(_._2)
        .toMat(Sink.seq)(Keep.right)
    )(Keep.right)
    .flatMapConcat(_._1)
    .named("flatMapConcatMat")
  }

  def failPromiseOnFailure[T, PT](promise: Promise[PT]) = {
    Sink.onComplete[T](_.failed.foreach(promise.tryFailure))
  }

  def failPromiseOnComplete[T, PT](promise: Promise[PT]) = Sink.onComplete[T] {
    case Success(_) ⇒ promise.tryFailure(new Exception("Stream completed"))
    case Failure(error) ⇒ promise.tryFailure(error)
  }

  def writeInputStream[T, M](f: InputStream ⇒ Graph[SourceShape[T], M]): Flow[ByteString, T, Future[Seq[M]]] = {
    Flow[ByteString]
      .via(extractUpstream)
      .flatMapConcat { byteStream ⇒
        val promise = Promise[InputStream]
        byteStream
          .alsoTo(Flow[ByteString].async
            .toMat(StreamConverters.asInputStream(15 seconds))(Keep.right)
            .mapMaterializedValue(promise.success))
          .alsoTo(failPromiseOnFailure(promise))
          .via(dropUpstream(Source.fromFuture(promise.future)))
      }
      .async
      .viaMat(flatMapConcatMat { inputStream ⇒
        val graph = concurrent.blocking(f(inputStream))
        Source.fromGraph(graph).mapMaterializedValue(Future.successful)
      })(Keep.right)
      .named("writeInputStream")
  }
}
