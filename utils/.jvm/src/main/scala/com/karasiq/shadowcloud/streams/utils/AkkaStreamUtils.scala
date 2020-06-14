package com.karasiq.shadowcloud.streams.utils

import java.io.InputStream

import akka.event.Logging
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, FlowOpsMat, GraphDSL, Keep, Sink, Source, StreamConverters}
import akka.util.ByteString
import akka.{Done, NotUsed}

import scala.annotation.unchecked.uncheckedVariance
import scala.concurrent.duration.{Duration, FiniteDuration, _}
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

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

  def cancelUpstream[T, T1, M](newStream: Source[T1, M]): Flow[T, T1, M] = {
    Flow.fromSinkAndSourceMat(Sink.cancelled, newStream)(Keep.right).named("cancelUpstream")
  }

  def ignoreUpstream[T, T1, M](newStream: Source[T1, M]): Flow[T, T1, M] = {
    Flow.fromSinkAndSourceMat(Sink.ignore, newStream)(Keep.right).named("ignoreUpstream")
  }

  def waitUpstream[T, T1, M](newStream: Source[T1, M]): Flow[T, T1, M] = {
    val promise = Promise[Done]
    val sink = Flow[T]
      .watchTermination() { (_, f) ⇒
        promise.completeWith(f)
        NotUsed
      }
      .to(Sink.ignore)
    val waitingStream = newStream.zipLatest(Source.future(promise.future)).map(_._1)
    Flow.fromSinkAndSourceMat(sink, waitingStream)(Keep.right).named("waitUpstream")
  }

  def extractUpstreamAndMat[I, O, M](stream: Graph[FlowShape[I, O], M]): Flow[I, (Source[O, NotUsed], Future[M]), NotUsed] = {
    val promise = Promise[M]
    Flow
      .fromGraph(stream)
      .alsoTo(failPromiseOnComplete(promise))
      .mapMaterializedValue { mat ⇒
        promise.trySuccess(mat); NotUsed
      }
      .via(extractUpstream)
      .zip(Source.single(promise.future))
      .named("extractUpstreamAndMat")
  }

  def flatMapConcatMat[E, E1, M](f: E ⇒ Graph[SourceShape[E1], Future[M]]): Flow[E, E1, Future[Seq[M]]] = {
    Flow[E]
      .map { element ⇒
        val promise = Promise[M]
        val stream = Source
          .fromGraph(f(element))
          .alsoTo(failPromiseOnFailure(promise))
          .mapMaterializedValue { f ⇒
            promise.completeWith(f); NotUsed
          }
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
    case Success(_)     ⇒ promise.tryFailure(new Exception("Stream completed"))
    case Failure(error) ⇒ promise.tryFailure(error)
  }

  def successPromiseOnFirst[PT](promise: Promise[PT]) =
    Flow[PT]
      .alsoTo(Sink.foreach(promise.trySuccess))
      .to(Sink.onComplete {
        case Failure(err) ⇒ promise.tryFailure(err)
        case Success(_)   ⇒ promise.tryFailure(new IllegalStateException("Stream finished"))
      })

  def alsoToWaitForAll[Out, M](that: Graph[SinkShape[Out], M]): Graph[FlowShape[Out @uncheckedVariance, Out], M] =
    GraphDSL.create(that) { implicit b ⇒ r ⇒
      import GraphDSL.Implicits._
      val bcast = b.add(Broadcast[Out](2, eagerCancel = false))
      bcast.out(1) ~> r
      FlowShape(bcast.in, bcast.out(0))
    }

  def writeInputStream[T, M](
      f: InputStream ⇒ Graph[SourceShape[T], M],
      dispatcher: ActorAttributes.Dispatcher = ActorAttributes.IODispatcher
  ): Flow[ByteString, T, Future[Seq[M]]] = {
    Flow[ByteString]
      .via(extractUpstream)
      .flatMapConcat { byteStream ⇒
        val promise = Promise[InputStream]
        byteStream.async
          .alsoTo(
            Flow[ByteString]
              .toMat(StreamConverters.asInputStream(15 seconds))(Keep.right)
              .mapMaterializedValue(promise.success)
          )
          .alsoTo(failPromiseOnFailure(promise))
          .via(Flow.fromSinkAndSource(Sink.ignore, Source.future(promise.future)))
      }
      .viaMat(flatMapConcatMat[InputStream, T, M] { inputStream ⇒
        val graph = concurrent.blocking(f(inputStream))
        Source.fromGraph(graph).mapMaterializedValue(Future.successful)
      }.withAttributes(Attributes(dispatcher)))(Keep.right)
      .named("writeInputStream")
  }

  def logInfoLevel: Attributes =
    ActorAttributes.logLevels(Logging.InfoLevel, Logging.InfoLevel)

  object Implicits {
    class FlowExt[E, Mat, Repr[`E`, `Mat`] <: FlowOpsMat[E, Mat]](val flow: Repr[E, Mat]) {
      def extractMatFuture[T](implicit ev: Mat ⇒ Future[T]): Repr[T, NotUsed] =
        extractMatValue.flatMapConcat(m ⇒ Source.future(ev(m))).asInstanceOf[Repr[T, NotUsed]]

      def extractMatValue: Repr[Mat, NotUsed] = {
        val promise = Promise[Mat]
        flow
          .alsoTo(failPromiseOnFailure(promise))
          .mapMaterializedValue { mat ⇒
            promise.trySuccess(mat)
            NotUsed
          }
          .viaMat(ignoreUpstream(Source.future(promise.future)))(Keep.none)
          .asInstanceOf[Repr[Mat, NotUsed]]
      }

      def flatMapConcatMat[E1, M, M1](f: E ⇒ Graph[SourceShape[E1], Future[M]])(matF: (Mat, Future[Seq[M]]) ⇒ M1): Repr[E1, M1] = {
        flow.viaMat(AkkaStreamUtils.flatMapConcatMat(f))(matF).asInstanceOf[Repr[E1, M1]]
      }

      def flatMapConcatMatRight[E1, M](f: E ⇒ Graph[SourceShape[E1], Future[M]]): Repr[E1, Future[Seq[M]]] =
        flatMapConcatMat(f)(Keep.right)
    }

    implicit def sourceExt[T, M](g: Source[T, M]) = new FlowExt[T, M, Source](g)
  }
}
