package com.karasiq.shadowcloud.storage.inmem

import java.io.IOException

import akka.stream.IOResult
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

private[inmem] final class TrieMapStreams[K, V](map: TrieMap[K, V], length: V ⇒ Int) {
  def keys: Source[K, NotUsed] = {
    Source.fromIterator(() ⇒ map.keysIterator)
  }

  def read(key: K): Source[V, Future[IOResult]] = {
    map.get(key) match {
      case Some(data) ⇒
        Source.single(data)
          .mapMaterializedValue(_ ⇒ Future.successful(IOResult(length(data), Success(Done))))

      case None ⇒
        val exception = new IllegalArgumentException(s"Chunk not found: $key")
        Source.failed(exception)
          .mapMaterializedValue(_ ⇒ Future.successful(IOResult(0, Failure(exception))))
    }
  }

  def write(key: K): Sink[V, Future[IOResult]] = {
    if (map.contains(key)) {
      Sink.cancelled.mapMaterializedValue(_ ⇒ {
        val exception = new IOException(s"Chunk already exists: $key")
        Future.successful(IOResult(0, Failure(exception)))
      })
    } else {
      val result = Promise[IOResult]
      Flow[V]
        .map(data ⇒ (map.putIfAbsent(key, data), data))
        .takeWhile(_._1.isEmpty)
        .alsoTo(Sink.foreach { case (_, data) ⇒
          result.success(IOResult(length(data), Success(Done)))
        })
        .to(Sink.onComplete { _ ⇒
          result.trySuccess(IOResult(0, Failure(new IOException("Chunk write error"))))
        })
        .mapMaterializedValue(_ ⇒ result.future)
    }
  }
}
