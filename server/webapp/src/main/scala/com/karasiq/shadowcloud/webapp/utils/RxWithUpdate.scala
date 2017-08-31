package com.karasiq.shadowcloud.webapp.utils

import scala.concurrent.{ExecutionContext, Future}

import rx._

object RxWithUpdate {
  def apply[T](initial: T)(getValue: T ⇒ Future[T])(implicit ctx: Ctx.Owner, ec: ExecutionContext): RxWithUpdate[T] = {
    new RxWithUpdate(initial, getValue)
  }
}

class RxWithUpdate[T](initial: T, getValue: T ⇒ Future[T])
                     (implicit ctx: Ctx.Owner, ec: ExecutionContext) extends HasUpdate {

  final val counter = Var(0)
  protected final val value = Var(initial)

  counter.trigger {
    getValue(value.now).foreach(value() = _)
  }

  def toRx: Rx[T] = {
    value
  }

  def update(): Unit = {
    counter() = counter.now + 1
  }
}