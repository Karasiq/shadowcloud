package com.karasiq.shadowcloud.webapp.components.common

import scala.language.postfixOps
import scalatags.JsDom.all.{a, href, onclick}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import rx.{Rx, Var}

object AppComponents {
  def iconLink(title: Modifier, icon: Modifier, md: Modifier*): ConcreteHtmlTag[dom.html.Anchor] = a(
    href := "javascript:void(0)",
    icon,
    Bootstrap.nbsp,
    title,
    md
  )

  def dropDownLink(title: String, opened: Var[Boolean]): ConcreteHtmlTag[dom.html.Anchor] = {
    iconLink(title, Rx(if (opened()) "▼" else "►"), onclick := Callback.onClick(_ ⇒ opened() = !opened.now))
  }
}
