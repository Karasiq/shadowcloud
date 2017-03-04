package com.karasiq.shadowcloud.actors.internal

import akka.Done
import akka.stream.IOResult

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

private[actors] object AkkaUtils {
  def unwrapIOResult(future: Future[IOResult])(implicit ec: ExecutionContext): Future[Long] = {
    future
      .recover { case error ⇒ IOResult(0, Failure(error)) }
      .map {
        case IOResult(written, Success(Done)) ⇒ written
        case IOResult(_, Failure(error)) ⇒ throw error
      }
  }

  def onIOComplete(future: Future[IOResult])(pf: PartialFunction[Try[Long], Unit])(implicit ec: ExecutionContext): Unit = {
    unwrapIOResult(future).onComplete(pf)
  }
}
