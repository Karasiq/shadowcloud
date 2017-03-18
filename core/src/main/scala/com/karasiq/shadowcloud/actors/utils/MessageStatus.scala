package com.karasiq.shadowcloud.actors.utils

import akka.actor.DeadLetterSuppression

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

trait MessageStatus[Key, Value] {
  sealed abstract class Status extends DeadLetterSuppression {
    def key: Key
  }
  case class Success(key: Key, result: Value) extends Status
  case class Failure(key: Key, error: Throwable) extends Status

  def wrapFuture(key: Key, future: Future[Value])(implicit ec: ExecutionContext): Future[this.Status] = {
    future
      .map(this.Success(key, _))
      .recover { case error ⇒ this.Failure(key, error) }
  }

  def unwrapFuture(future: Future[_])(implicit ec: ExecutionContext): Future[Value] = {
    future.flatMap {
      case this.Success(_, value) ⇒
        Future.successful(value)

      case this.Failure(_, error) ⇒
        Future.failed(error)

      case _ ⇒
        Future.failed(new IllegalStateException())
    }
  }
}