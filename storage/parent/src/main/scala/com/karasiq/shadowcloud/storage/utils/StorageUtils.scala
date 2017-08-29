package com.karasiq.shadowcloud.storage.utils

import java.io.FileNotFoundException
import java.nio.file.FileAlreadyExistsException

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.Done
import akka.stream.{IOResult ⇒ AkkaIOResult}
import akka.util.ByteString

import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.utils.HexString

object StorageUtils {
  def toStoragePath(key: Any, root: Path = Path.root): Path = {
    def encodeNode(node: Any): String = node match {
      case bytes: ByteString ⇒
        HexString.encode(bytes)

      case _ ⇒
        node.toString
    }

    key match {
      case path: Path ⇒
        root / path

      case (v1, v2) ⇒
        root / encodeNode(v1) / encodeNode(v2)

      case _ ⇒
        root / key.toString
    }
  }

  def wrapException(path: Path, error: Throwable): StorageException = error match {
    case se: StorageException ⇒
      se

    case _: FileNotFoundException | _: NoSuchElementException ⇒
      StorageException.NotFound(path)

    case _: FileAlreadyExistsException ⇒
      StorageException.AlreadyExists(path)

    case _ ⇒
      StorageException.IOFailure(path, error)
  }

  def wrapIOResult(path: Path, future: Future[AkkaIOResult])(implicit ec: ExecutionContext): Future[StorageIOResult] = {
    wrapFuture(path, future.map {
      case AkkaIOResult(count, Success(Done)) ⇒
        StorageIOResult.Success(path, count)

      case AkkaIOResult(_, Failure(error)) ⇒
        StorageIOResult.Failure(path, StorageUtils.wrapException(path, error))
    })
  }

  def wrapFuture(path: Path, future: Future[_ <: StorageIOResult])(implicit ec: ExecutionContext): Future[StorageIOResult] = {
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
    if (results.isEmpty) return StorageIOResult.empty
    val path = results.headOption.fold(Path.root)(_.path)
    val count = results
      .collect { case StorageIOResult.Success(_, count) ⇒ count }
      .sum
    StorageIOResult.Success(path, count)
  }

  def foldIOResults(results: StorageIOResult*): StorageIOResult = {
    if (results.isEmpty) return StorageIOResult.empty
    results.find(_.isFailure).getOrElse(foldIOResultsIgnoreErrors(results:_*))
  }

  def foldIOFutures(fs: Future[StorageIOResult]*)(implicit ec: ExecutionContext): Future[StorageIOResult] = {
    val future = Future.sequence(fs).map(foldIOResults)
    StorageUtils.wrapFuture("", future)
  }
}
