package com.karasiq.shadowcloud.storage.repository.wrappers

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.{CategorizedRepository, Repository}
import com.karasiq.shadowcloud.storage.utils.StorageUtils
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils

private[repository] final class SubRepositoriesWrapper[CatKey, Key](pathString: String,
                                                                    subRepositories: () ⇒ Source[(CatKey, Repository[Key]), NotUsed])
                                                                   (implicit ec: ExecutionContext)
  extends CategorizedRepository[CatKey, Key] {
  
  override def subRepository(categoryKey: CatKey): Repository[Key] = {
    new Repository[Key] {
      private[this] def openSubStream[T, M](extractSource: Repository[Key] ⇒ Source[T, M]): Source[T, Future[M]] = {
        val promise = Promise[M]
        subRepositories()
          .filter(_._1 == categoryKey)
          .map(_._2)
          .take(1)
          .orElse(Source.failed(new NoSuchElementException(categoryKey.toString)))
          .flatMapConcat(repo ⇒ extractSource(repo).mapMaterializedValue(promise.trySuccess))
          .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
          .mapMaterializedValue(_ ⇒ promise.future)
      }

      def read(key: Key): Source[Data, Result] = {
        openSubStream(_.read(key)).mapMaterializedValue(_.flatten)
      }

      def keys: Source[Key, Result] = {
        val promise = Promise[StorageIOResult]
        val repositories = subRepositories()
        repositories
          .filter(_._1 == categoryKey)
          .map(_._2)
          .flatMapConcat(_.keys)
          .alsoTo(Sink.onComplete {
            case Success(_) ⇒
              promise.success(StorageIOResult.Success(pathString, 0))

            case Failure(error) ⇒
              promise.failure(error)
          })
          .mapMaterializedValue(_ ⇒ promise.future)
      }

      def write(key: Key): Sink[Data, Result] = {
        val promise = Promise[Result]
        Flow[Data]
          .via(AkkaStreamUtils.extractUpstream)
          .flatMapConcat { source ⇒
            openSubStream(repo ⇒ source.alsoToMat(repo.write(key))(Keep.right))
              .map(_ ⇒ NotUsed)
              .mapMaterializedValue(promise.completeWith)
          }
          .to(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
          .mapMaterializedValue(_ ⇒ promise.future.flatten)
      }

      def delete: Sink[Key, Result] = {
        Flow[Key]
          .prefixAndTail(0)
          .map(_._2)
          .zip(openSubStream(repo ⇒ Source.single(repo.delete)))
          .map { case (source, sink) ⇒
            val promise = Promise[StorageIOResult]
            val stream = source
              .alsoToMat(sink)(Keep.right)
              .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
              .mapMaterializedValue { future ⇒ promise.completeWith(future); NotUsed }
            (stream, promise.future)
          }
          .alsoToMat(
            Flow[(Source[Key, NotUsed], Result)]
              .mapAsync(1)(_._2)
              .fold(Seq.empty[StorageIOResult])(_ :+ _)
              .map(results ⇒ StorageUtils.foldIOResultsIgnoreErrors(results:_*))
              .toMat(Sink.head)(Keep.right)
          )(Keep.right)
          .flatMapConcat(_._1)
          .to(Sink.ignore)
      }
    }
  }

  def keys: Source[(CatKey, Key), Result] = {
    val promise = Promise[StorageIOResult]
    val repositories = subRepositories()
    repositories
      .flatMapConcat { case (ck, repo) ⇒
        repo.keys.map((ck, _))
      }
      .alsoTo(Sink.onComplete {
        case Success(_) ⇒
          promise.success(StorageIOResult.Success(pathString, 0))

        case Failure(error) ⇒
          promise.failure(error)
      })
      .mapMaterializedValue(_ ⇒ promise.future)
  }

  def read(key: (CatKey, Key)): Source[Data, Result] = {
    subRepository(key._1).read(key._2)
  }

  def write(key: (CatKey, Key)): Sink[Data, Result] = {
    subRepository(key._1).write(key._2)
  }

  def delete: Sink[(CatKey, Key), Result] = {
    Flow[(CatKey, Key)]
      .groupBy(100, _._1)
      .prefixAndTail(1)
      .map { case (head +: Nil, source) ⇒
        val promise = Promise[StorageIOResult]
        val repository = subRepository(head._1)
        val stream = Source.single(head)
          .concat(source)
          .map(_._2)
          .alsoToMat(repository.delete)(Keep.right)
          .alsoTo(Sink.onComplete(_.failed.foreach(promise.tryFailure)))
          .mapMaterializedValue { future ⇒ promise.completeWith(future); NotUsed }
        (stream, promise.future)
      }
      .mergeSubstreams
      .alsoToMat(
        Flow[(Source[Key, NotUsed], Result)]
          .mapAsync(1)(_._2)
          .fold(Seq.empty[StorageIOResult])(_ :+ _)
          .map(results ⇒ StorageUtils.foldIOResultsIgnoreErrors(results:_*))
          .toMat(Sink.head)(Keep.right)
      )(Keep.right)
      .flatMapConcat(_._1)
      .to(Sink.ignore)
  }
}
