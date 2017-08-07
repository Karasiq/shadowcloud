package com.karasiq.shadowcloud.storage.repository.wrappers

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.{Done, NotUsed}
import akka.actor.Status
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}

import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.repository.{CategorizedRepository, Repository}
import com.karasiq.shadowcloud.storage.utils.StorageUtils

private[repository] final class SubRepositoriesWrapper[CatKey, Key](pathString: String,
                                                                    subRepositories: () ⇒ Source[(CatKey, Repository[Key]), NotUsed])
                                                                   (implicit ec: ExecutionContext, mat: Materializer)
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
          .alsoTo(Sink.onComplete(result ⇒ if (result.isFailure) promise.tryFailure(result.failed.get)))
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
          .prefixAndTail(0)
          .map(_._2)
          .flatMapConcat { source ⇒
            openSubStream(repo ⇒ source.alsoToMat(repo.write(key))(Keep.right))
              .map(_ ⇒ NotUsed)
              .mapMaterializedValue(promise.completeWith)
          }
          .to(Sink.onComplete(result ⇒ if (result.isFailure) promise.tryFailure(result.failed.get)))
          .mapMaterializedValue(_ ⇒ promise.future.flatten)
      }

      def delete: Sink[Key, Result] = {
        val (matSink, matResult) = Source.actorRef[Result](10, OverflowStrategy.dropNew)
          .mapAsync(4)(identity)
          .idleTimeout(20 seconds)
          .fold(Seq.empty[StorageIOResult])(_ :+ _)
          .map(results ⇒ StorageUtils.foldIOResultsIgnoreErrors(results:_*))
          .toMat(Sink.head)(Keep.both)
          .run()

        Flow[Key]
          .prefixAndTail(0)
          .map(_._2)
          .zip(openSubStream(repo ⇒ Source.single(repo.delete)))
          .flatMapConcat { case (source, sink) ⇒
            source
              .alsoToMat(sink)(Keep.right)
              .mapMaterializedValue(matSink ! _)
          }
          .alsoTo(Sink.onComplete(_ ⇒ matSink ! Status.Success(Done)))
          .to(Sink.ignore)
          .mapMaterializedValue(_ ⇒ matResult)
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
    val (matSink, matResult) = Source.actorRef[Result](10, OverflowStrategy.dropNew)
      .mapAsync(4)(identity)
      .idleTimeout(20 seconds)
      .fold(Seq.empty[StorageIOResult])(_ :+ _)
      .map(results ⇒ StorageUtils.foldIOResultsIgnoreErrors(results:_*))
      .toMat(Sink.head)(Keep.both)
      .run()

    Flow[(CatKey, Key)]
      .groupBy(100, _._1)
      .prefixAndTail(1)
      .flatMapConcat { case (head +: Nil, source) ⇒
        val repository = subRepository(head._1)
        Source.single(head)
          .concat(source)
          .map(_._2)
          .alsoToMat(repository.delete)(Keep.right)
          .mapMaterializedValue(matSink ! _)
      }
      .to(Sink.onComplete(_ ⇒ matSink ! Status.Success(Done)))
      .mapMaterializedValue(_ ⇒ matResult)
  }
}
