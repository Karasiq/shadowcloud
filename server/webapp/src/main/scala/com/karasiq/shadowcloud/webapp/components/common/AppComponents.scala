package com.karasiq.shadowcloud.webapp.components.common

import scala.language.postfixOps
import scalatags.JsDom.all.{a, href, onclick}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import rx.{Rx, Var}

import com.karasiq.taboverridejs.TabOverride

object AppComponents {
  def iconLink(title: Modifier, icon: Modifier, md: Modifier*): ConcreteHtmlTag[dom.html.Anchor] = a(
    href := "javascript:void(0)",
    icon,
    Bootstrap.nbsp,
    title,
    md
  )

  def dropdownLink(title: String, opened: Var[Boolean]): ConcreteHtmlTag[dom.html.Anchor] = {
    iconLink(title, Rx(if (opened()) "▼" else "►"), onclick := Callback.onClick(_ ⇒ opened() = !opened.now))
  }

  def dropdown(title: String)(content: ⇒ Modifier): ConcreteHtmlTag[dom.html.Div] = {
    val opened = Var(false)
    div(
      dropdownLink(title, opened),
      Rx[Frag](if (opened()) div(content) else ())
    )
  }

  def tabOverride: Modifier = { elem ⇒
    TabOverride.set(elem.asInstanceOf[dom.html.TextArea])
  }
}
