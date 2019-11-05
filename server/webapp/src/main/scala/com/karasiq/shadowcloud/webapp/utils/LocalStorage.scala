package com.karasiq.shadowcloud.webapp.utils

import org.scalajs.dom
import org.scalajs.dom.window.localStorage
import rx.{Ctx, Var}
import rx.async.Platform.DefaultScheduler
import rx.async._

import scala.concurrent.duration._

object LocalStorage {
  def memoize(key: String)(implicit owner: Ctx.Owner): Var[String] = {
    val value            = Var(Option(localStorage.getItem(key)).getOrElse(""))
    def updateLS(): Unit = localStorage.setItem(key, value.now)
    value.debounce(300 millis).triggerLater(updateLS())
    dom.window.addEventListener("unload", (_: dom.Event) => updateLS())
    value
  }
}
