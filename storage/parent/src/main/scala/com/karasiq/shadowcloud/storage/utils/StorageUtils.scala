package com.karasiq.shadowcloud.storage.utils

import java.io.FileNotFoundException
import java.nio.file.FileAlreadyExistsException

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.Done
import akka.stream.{IOResult ⇒ AkkaIOResult}

import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.storage.StorageIOResult

object StorageUtils {
  def wrapException(path: String, error: Throwable): StorageException = error match {
    case se: StorageException ⇒
      se

    case _: FileNotFoundException | _: NoSuchElementException ⇒
      StorageException.NotFound(path)

    case _: FileAlreadyExistsException ⇒
      StorageException.AlreadyExists(path)

    case _ ⇒
      StorageException.IOFailure(path, error)
  }

  def wrapIOResult(path: String, future: Future[AkkaIOResult])(implicit ec: ExecutionContext): Future[StorageIOResult] = {
    wrapFuture(path, future.map {
      case AkkaIOResult(count, Success(Done)) ⇒
        StorageIOResult.Success(path, count)

      case AkkaIOResult(_, Failure(error)) ⇒
        StorageIOResult.Failure(path, StorageUtils.wrapException(path, error))
    })
  }

  def wrapFuture(path: String, future: Future[_ <: StorageIOResult])(implicit ec: ExecutionContext): Future[StorageIOResult] = {
    future.recover { case error ⇒ StorageIOResult.Failure(path, StorageUtils.wrapException(path, error)) }
  }

  def unwrapFuture(future: Future[_ <: StorageIOResult])(implicit ec: ExecutionContext): Future[StorageIOResult] = {
    future.flatMap {
      case r: StorageIOResult.Success ⇒
        Future.successful(r)

      case StorageIOResult.Failure(_, error) ⇒
        Future.failed(error)
    }
  }

  def foldIOResultsIgnoreErrors(results: StorageIOResult*): StorageIOResult = {
    if (results.isEmpty) return StorageIOResult.Success("", 0L)
    val path = results.headOption.fold("")(_.path)
    val count = results
      .collect { case StorageIOResult.Success(_, count) ⇒ count }
      .sum
    StorageIOResult.Success(path, count)
  }

  def foldIOResults(results: StorageIOResult*): StorageIOResult = {
    if (results.isEmpty) return StorageIOResult.Success("", 0L)
    results.find(_.isFailure).getOrElse(foldIOResultsIgnoreErrors(results:_*))
  }

  def foldIOFutures(fs: Future[StorageIOResult]*)(implicit ec: ExecutionContext): Future[StorageIOResult] = {
    val future = Future.sequence(fs).map(foldIOResults)
    StorageUtils.wrapFuture("", future)
  }
}
