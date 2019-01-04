package com.karasiq.shadowcloud.webapp.utils

import rx.{Ctx, Var}

object LSBind {
  private[this] val LS = org.scalajs.dom.window.localStorage

  def apply[T](name: String, default: T)(implicit ctx: Ctx.Owner, toString: T ⇒ String, fromString: String ⇒ T): Var[T] = {
    val initialValue = Option(LS.getItem(name))
    val value = Var[T](initialValue.fold(default)(fromString))
    value.triggerLater(LS.setItem(name, value.now))
    value
  }
}
