package com.karasiq.shadowcloud.webapp.utils

import scala.concurrent.{ExecutionContext, Future}

import rx._

case class RxWithUpdate[T](initial: T)(getValue: T ⇒ Future[T])(implicit ctx: Ctx.Owner, ec: ExecutionContext) {
  private[this] val counter = Var(0)
  private[this] val value = Var(initial)

  counter.foreach { _ ⇒
    getValue(value.now).foreach(value() = _)
  }

  def toRx: Rx[T] = {
    value
  }

  def update(): Unit = {
    counter() = counter.now + 1
  }
}