package com.karasiq.shadowcloud.storage

import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait Repository[Key] {
  final type Data = ByteString
  final type Result = Future[IOResult]

  def keys: Source[Key, Result]
  def read(key: Key): Source[Data, Result]
  def write(key: Key): Sink[Data, Result]
}

object Repository {
  def mapKeys[OldKey, NewKey](repository: Repository[OldKey], toNew: OldKey ⇒ NewKey,
                              toOld: NewKey ⇒ OldKey): Repository[NewKey] = {
    new Repository[NewKey] {
      def keys: Source[NewKey, Result] = repository.keys.map(toNew)
      def read(key: NewKey): Source[Data, Result] = repository.read(toOld(key))
      def write(key: NewKey): Sink[Data, Result] = repository.write(toOld(key))
    }
  }

  def mapSubKeys[CatKey, OldKey, NewKey](repository: CategorizedRepository[CatKey, OldKey], toNew: OldKey ⇒ NewKey,
                                         toOld: NewKey ⇒ OldKey): CategorizedRepository[CatKey, NewKey] = {
    new CategorizedRepository[CatKey, NewKey] {
      def keys: Source[(CatKey, NewKey), Result] = repository.keys.map(key ⇒ key.copy(_2 = toNew(key._2)))
      def read(key: (CatKey, NewKey)): Source[Data, Result] = repository.read(key.copy(_2 = toOld(key._2)))
      def write(key: (CatKey, NewKey)): Sink[Data, Result] = repository.write(key.copy(_2 = toOld(key._2)))
    }
  }

  def forIndex[CatKey](repository: CategorizedRepository[CatKey, String]): CategorizedRepository[CatKey, Long] = {
    mapSubKeys[CatKey, String, Long](repository, _.toLong, _.toString)
  }

  def toSeq[Key](repository: Repository[Key]): SeqRepository[Key] = new SeqRepository[Key] {
    def keys: Source[Key, Result] = repository.keys
    def read(key: Key): Source[Data, Result] = repository.read(key)
    def write(key: Key): Sink[Data, Result] = repository.write(key)
  }

  def toCategorized[CatKey, Key](repository: Repository[(CatKey, Key)]): CategorizedRepository[CatKey, Key] = {
    new CategorizedRepository[CatKey, Key] {
      def keys: Source[(CatKey, Key), Result] = repository.keys
      def read(key: (CatKey, Key)): Source[Data, Result] = repository.read(key)
      def write(key: (CatKey, Key)): Sink[Data, Result] = repository.write(key)
    }
  }

  def fromSubRepositories[CatKey, Key](subRepositories: () ⇒ Map[CatKey, Repository[Key]])
                                      (implicit ec: ExecutionContext): CategorizedRepository[CatKey, Key] = {
    new CategorizedRepository[CatKey, Key] {
      override def subRepository(key: CatKey): Repository[Key] = {
        val map = subRepositories()
        map(key)
      }

      def keys: Source[(CatKey, Key), Result] = {
        def combineIoResults(fs: Future[IOResult]*): Future[IOResult] = {
          Future.sequence(fs).map { results ⇒
            val failures = results.map(_.status).filter(_.isFailure)
            val count = results.foldLeft(0L)(_ + _.count)
            val status = failures.headOption.getOrElse(Success(Done))
            IOResult(count, status)
          }
        }
        val keySources = subRepositories().map { case (catKey, repo) ⇒ repo.keys.map((catKey, _)) }
        val emptySrc = Source.empty[(CatKey, Key)].mapMaterializedValue(_ ⇒ Future.successful(IOResult(0, Success(Done))))
        keySources.foldLeft(emptySrc)((s1, s2) ⇒ s1.concatMat(s2)(combineIoResults(_, _)))
      }

      def read(key: (CatKey, Key)): Source[Data, Result] = {
        subRepository(key._1).read(key._2)
      }

      def write(key: (CatKey, Key)): Sink[Data, Result] = {
        subRepository(key._1).write(key._2)
      }
    }
  }
}
