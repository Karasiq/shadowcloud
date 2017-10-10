package com.karasiq.shadowcloud.webapp.utils

import scala.scalajs.js.{UndefOr, URIUtils}

import org.scalajs.dom
import org.scalajs.dom.window
import rx._

object RxLocation {
  def apply()(implicit ctx: Ctx.Owner): RxLocation = {
    new RxLocation()
  }
}

final class RxLocation(implicit ctx: Ctx.Owner) {
  val hash: Var[Option[String]] = Var(readLocationHash(window.location.hash))

  hash.triggerLater {
    window.location.hash = hash.now.fold("")("#" + _)
  }

  dom.window.addEventListener("hashchange", { e ⇒
    hash() = readLocationHash(window.location.hash)
  })

  /* jQuery(dom.window).on("hashchange", () ⇒ {
    hash() = readLocationHash(window.location.hash)
  }) */

  private[this] def readLocationHash(hash: UndefOr[String]): Option[String] = {
    hash
      .filter(_.nonEmpty)
      .map(hs ⇒ URIUtils.decodeURIComponent(hs.tail))
      .toOption
  }
}
