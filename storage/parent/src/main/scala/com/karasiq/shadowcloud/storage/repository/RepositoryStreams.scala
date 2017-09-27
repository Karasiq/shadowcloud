package com.karasiq.shadowcloud.storage.repository

import scala.concurrent.{Future, Promise}

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.utils.AkkaStreamUtils

object RepositoryStreams {
  val DefaultRetries = 3

  def writeWithRetries[K](repository: Repository[K], key: K, retries: Int = DefaultRetries): Sink[repository.Data, repository.Result] = {
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
            .named("read")
        }

        openStream().recoverWithRetries(retries, { case _ ⇒ openStream() })
      }
      .via(StorageUtils.wrapStream(StorageUtils.toStoragePath(key)))
      .toMat(Sink.last)(Keep.right)
      .named("readWithRetries")
  }

  def readWithRetries[K](repository: Repository[K], key: K, retries: Int = DefaultRetries): Source[repository.Data, repository.Result] = {
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
          .via(StorageUtils.wrapStream(StorageUtils.toStoragePath(key)))
          .alsoToMat(Sink.last)(Keep.right)
          .to(Sink.ignore)
          .named("write")
      }(Keep.right)
      .flatMapConcat { case (source, _) ⇒ source }
      .named("writeWithRetries")
  }
}
