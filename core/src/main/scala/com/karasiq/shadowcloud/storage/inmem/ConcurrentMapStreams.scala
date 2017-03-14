package com.karasiq.shadowcloud.storage.inmem

import java.io.IOException

import akka.stream.scaladsl.{Flow, Sink, Source}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.storage.StorageIOResult

import scala.collection.concurrent.{Map => CMap}
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps

private[inmem] final class ConcurrentMapStreams[K, V](map: CMap[K, V], length: V ⇒ Int) {
  def keys: Source[K, Future[StorageIOResult]] = {
    val keys = map.keys.toVector
    Source(keys)
      .mapMaterializedValue(_ ⇒ Future.successful(StorageIOResult.Success(rootPathString, keys.length)))
  }

  def read(key: K): Source[V, Future[StorageIOResult]] = {
    val path = toPathString(key)
    map.get(key) match {
      case Some(data) ⇒
        Source.single(data)
          .mapMaterializedValue(_ ⇒ Future.successful(StorageIOResult.Success(path, length(data))))

      case None ⇒
        Source.failed(StorageException.NotFound(path))
          .mapMaterializedValue(_ ⇒ Future.successful(StorageIOResult.Failure(path, StorageException.NotFound(path))))
    }
  }

  def write(key: K): Sink[V, Future[StorageIOResult]] = {
    val path = toPathString(key)
    if (map.contains(key)) {
      Sink.cancelled.mapMaterializedValue(_ ⇒ {
        Future.successful(StorageIOResult.Failure(path, StorageException.AlreadyExists(path)))
      })
    } else {
      val result = Promise[StorageIOResult]
      Flow[V]
        .map(data ⇒ (map.putIfAbsent(key, data), data))
        .limit(1)
        .alsoTo(Sink.foreach { case (oldValue, data) ⇒
          if (oldValue.isEmpty) {
            result.success(StorageIOResult.Success(path, length(data)))
          } else {
            result.success(StorageIOResult.Failure(path, StorageException.AlreadyExists(path)))
          }
        })
        .to(Sink.onComplete { _ ⇒
          result.trySuccess(StorageIOResult.Failure(path, StorageException.IOFailure(path, new IOException("No data written"))))
        })
        .mapMaterializedValue(_ ⇒ result.future)
    }
  }

  def delete(key: K): Future[StorageIOResult] = {
    val path = key.toString
    val ioResult = map.remove(key)
      .map(deleted ⇒ StorageIOResult.Success(path, length(deleted)))
      .getOrElse(StorageIOResult.Failure(path, StorageException.NotFound(path)))
    Future.successful(ioResult)
  }

  private[this] def rootPathString: String = {
    "ConcurrentMap"
  }

  private[this] def toPathString(key: K): String = {
    s"$key in $rootPathString"
  }
}
