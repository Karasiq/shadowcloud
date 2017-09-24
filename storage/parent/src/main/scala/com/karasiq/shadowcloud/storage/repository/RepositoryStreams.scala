package com.karasiq.shadowcloud.storage.repository

import java.io.IOException

import scala.concurrent.{Future, Promise}

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.utils.AkkaStreamUtils

object RepositoryStreams {
  def writeWithRetries[K](repository: Repository[K], key: K, retries: Int = 5): Sink[repository.Data, repository.Result] = {
    Flow[repository.Data]
      .via(AkkaStreamUtils.extractUpstream)
      .flatMapConcat { source ⇒
        def openStream() = {
          val promise = Promise[StorageIOResult]()
          source
            .alsoToMat(repository.write(key))(Keep.right)
            .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
            .mapMaterializedValue(promise.completeWith)
            .fold(NotUsed)((_, _) ⇒ NotUsed)
            .flatMapConcat(_ ⇒ Source.fromFuture(promise.future))
            .mapMaterializedValue(_ ⇒ NotUsed)
        }

        openStream().recoverWithRetries(retries, { case _ ⇒ openStream() })
      }
      .orElse(Source.single(StorageIOResult.Failure(Path.root, StorageException.IOFailure(Path.root, new IOException("No data written")))))
      .toMat(Sink.last)(Keep.right)
  }

  def readWithRetries[K](repository: Repository[K], key: K, retries: Int = 5): Source[repository.Data, repository.Result] = {
    def openStream() = {
      val promise = Promise[StorageIOResult]()
      repository.read(key)
        .mapMaterializedValue(promise.completeWith)
        .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
        .via(AkkaStreamUtils.extractUpstream)
        .flatMapConcat(source ⇒ Source.single(promise.future).map((source, _)))
        .mapMaterializedValue(_ ⇒ NotUsed)
    }

    openStream().recoverWithRetries(retries, { case _ ⇒ openStream() })
      .alsoToMat {
        Flow[(Source[ByteString, NotUsed], Future[StorageIOResult])]
          .flatMapConcat { case (_, future) ⇒ Source.fromFuture(future) }
          .orElse(Source.single(StorageIOResult.Failure(Path.root, StorageException.IOFailure(Path.root, new IOException("No data read")))))
          .alsoToMat(Sink.last)(Keep.right)
          .to(Sink.ignore)
      }(Keep.right)
      .flatMapConcat { case (source, _) ⇒ source }
  }
}
