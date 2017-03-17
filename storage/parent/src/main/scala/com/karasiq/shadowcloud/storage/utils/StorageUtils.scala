package com.karasiq.shadowcloud.storage.utils

import java.io.FileNotFoundException
import java.nio.file.FileAlreadyExistsException

import akka.Done
import akka.stream.{IOResult => AkkaIOResult}
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.storage.StorageIOResult

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

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
}
