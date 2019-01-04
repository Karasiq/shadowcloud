package com.karasiq.shadowcloud.webapp.utils

import org.scalajs.dom.Storage
import rx.{Ctx, Var}

trait StorageBind {
  protected def storage: Storage

  def apply[T](name: String, default: T)(implicit ctx: Ctx.Owner, toString: T ⇒ String, fromString: String ⇒ T): Var[T] = {
    val initialValue = Option(storage.getItem(name))
    val value = Var[T](initialValue.fold(default)(fromString))
    value.triggerLater(storage.setItem(name, value.now))
    value
  }
}

object StorageBind {
  // Local storage
  object LS extends StorageBind {
    protected val storage = org.scalajs.dom.window.localStorage
  }
}
