package com.karasiq.shadowcloud.storage.repository.wrappers

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.{CategorizedRepository, Repository}
import com.karasiq.shadowcloud.storage.utils.StorageUtils

private[repository] final class SubRepositoriesWrapper[CatKey, Key](pathString: String,
                                                                    subRepositories: () ⇒ Source[(CatKey, Repository[Key]), NotUsed])
                                                                   (implicit ec: ExecutionContext, mat: Materializer)
  extends CategorizedRepository[CatKey, Key] {
  
  override def subRepository(key: CatKey): Repository[Key] = {
    new Repository[Key] {
      private[this] def openSubStream[T, M](key: Key, f: Repository[Key] ⇒ Source[T, M]): Source[T, Future[M]] = {
        val promise = Promise[M]
        subRepositories()
          .filter(_._1 == key)
          .map(_._2)
          .take(1)
          .orElse(Source.failed(new NoSuchElementException(key.toString)))
          .flatMapConcat(repo ⇒ f(repo).mapMaterializedValue(promise.trySuccess))
          .alsoTo(Sink.onComplete(result ⇒ if (result.isFailure) promise.tryFailure(result.failed.get)))
          .mapMaterializedValue(_ ⇒ promise.future)
      }

      def read(key: Key): Source[Data, Result] = {
        openSubStream(key, _.read(key)).mapMaterializedValue(_.flatten)
      }

      def keys: Source[Key, Result] = {
        val promise = Promise[StorageIOResult]
        val repositories = subRepositories()
        repositories
          .filter(_._1 == key)
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

      def delete(key: Key): Result = {
        openSubStream(key, repo ⇒ Source.fromFuture(repo.delete(key)))
          .toMat(Sink.head)(Keep.right)
          .mapMaterializedValue(StorageUtils.wrapFuture(pathString, _))
          .run()
      }

      def write(key: Key): Sink[Data, Result] = {
        val promise = Promise[Result]
        Flow[Data]
          .prefixAndTail(0)
          .map(_._2)
          .flatMapConcat { source ⇒
            openSubStream(key, repo ⇒ source.alsoToMat(repo.write(key))(Keep.right))
              .map(_ ⇒ NotUsed)
              .mapMaterializedValue(promise.completeWith)
          }
          .to(Sink.onComplete(result ⇒ if (result.isFailure) promise.tryFailure(result.failed.get)))
          .mapMaterializedValue(_ ⇒ promise.future.flatten)
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

  def delete(key: (CatKey, Key)): Result = {
    subRepository(key._1).delete(key._2)
  }
}
