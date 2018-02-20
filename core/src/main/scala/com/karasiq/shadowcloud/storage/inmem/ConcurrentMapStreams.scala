package com.karasiq.shadowcloud.storage.inmem

import java.io.IOException

import scala.collection.concurrent.{Map ⇒ CMap}
import scala.concurrent.Future
import scala.language.postfixOps

import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.utils.StorageUtils

private[inmem] final class ConcurrentMapStreams[K, V](map: CMap[K, V], length: V ⇒ Int) {
  def keys: Source[K, Future[StorageIOResult]] = {
    Source.fromIterator(() ⇒ map.keysIterator)
      .alsoToMat(StorageUtils.countPassedElements().toMat(Sink.head)(Keep.right))(Keep.right)
      .named("concurrentMapKeys")
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
    Flow[V]
      .map { data ⇒
        val oldValue = map.putIfAbsent(key, data)
        if (oldValue.isEmpty) StorageIOResult.Success(path, length(data))
        else StorageIOResult.Failure(path, StorageException.AlreadyExists(path))
      }
      .orElse(Source.single(StorageIOResult.Failure(path, StorageException.IOFailure(path, new IOException("No data written")))))
      .toMat(Sink.head)(Keep.right)
      .named("concurrentMapWrite")
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
