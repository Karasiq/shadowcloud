package com.karasiq.shadowcloud.storage.wrappers

import akka.Done
import akka.stream.IOResult
import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.storage.{CategorizedRepository, Repository}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Success

private[storage] final class SubRepositoriesWrapper[CatKey, Key](subRepositories: () ⇒ Map[CatKey, Repository[Key]])
                                                                (implicit ec: ExecutionContext)
  extends CategorizedRepository[CatKey, Key] {
  
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

  def delete(key: (CatKey, Key)): Result = {
    subRepository(key._1).delete(key._2)
  }
}
