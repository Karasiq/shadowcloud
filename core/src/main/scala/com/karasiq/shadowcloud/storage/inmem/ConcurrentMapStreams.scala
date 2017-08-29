package com.karasiq.shadowcloud.storage.inmem

import java.io.IOException

import scala.collection.concurrent.{Map ⇒ CMap}
import scala.concurrent.{Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.utils.StorageUtils

private[inmem] final class ConcurrentMapStreams[K, V](map: CMap[K, V], length: V ⇒ Int) {
  def keys: Source[K, Future[StorageIOResult]] = {
    val promise = Promise[StorageIOResult]
    Source.fromIterator(() ⇒ map.keysIterator)
      .alsoTo(Sink.onComplete {
        case Success(_) ⇒
          promise.success(StorageIOResult.Success(Path.root, 0))

        case Failure(error) ⇒
          promise.success(StorageIOResult.Failure(Path.root, StorageUtils.wrapException(Path.root, error)))
      })
      .mapMaterializedValue(_ ⇒ promise.future)
  }

  def read(key: K): Source[V, Future[StorageIOResult]] = {
    val path = StorageUtils.toStoragePath(key)
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
    val path = StorageUtils.toStoragePath(key)
    if (map.contains(key)) {
      Sink.cancelled.mapMaterializedValue(_ ⇒ {
        Future.successful(StorageIOResult.Failure(path, StorageException.AlreadyExists(path)))
      })
    } else {
      val result = Promise[StorageIOResult]
      Flow[V]
        .limit(1)
        .map(data ⇒ (map.putIfAbsent(key, data), data))
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

  def delete: Sink[K, Future[StorageIOResult]] = {
    Flow[K]
      .map { key ⇒
        val path = StorageUtils.toStoragePath(key)
        map.remove(key)
          .map(deleted ⇒ StorageIOResult.Success(path, length(deleted)): StorageIOResult)
          .getOrElse(StorageIOResult.Failure(path, StorageException.NotFound(path)))
      }
      .fold(Seq.empty[StorageIOResult])(_ :+ _)
      .map(results ⇒ StorageUtils.foldIOResultsIgnoreErrors(results:_*))
      .toMat(Sink.head)(Keep.right)
  }
}
