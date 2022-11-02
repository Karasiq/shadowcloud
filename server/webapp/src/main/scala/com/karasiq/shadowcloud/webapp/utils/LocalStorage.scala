package com.karasiq.shadowcloud.webapp.utils

import org.scalajs.dom
import org.scalajs.dom.window.localStorage
import rx.async.Platform.DefaultScheduler
import rx.async._
import rx.{Ctx, Var}

import scala.collection.mutable
import scala.concurrent.duration._

object LocalStorage {
  private[this] val memoizedMap = mutable.Map.empty[String, Var[String]]

  def memoize(key: String)(implicit owner: Ctx.Owner): Var[String] =
    memoizedMap.getOrElseUpdate(
      key, {
        val value            = Var(Option(localStorage.getItem(key)).getOrElse(""))
        def updateLS(): Unit = localStorage.setItem(key, value.now)
        value.debounce(5 seconds).triggerLater(updateLS())
        dom.window.addEventListener("unload", (_: dom.Event) ⇒ updateLS())
        value
      }
    )
}
