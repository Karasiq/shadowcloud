package com.karasiq.shadowcloud.webapp.components.common

import scala.language.postfixOps
import scalatags.JsDom.all.{a, href, onclick}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom
import rx.{Rx, Var}

import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.taboverridejs.TabOverride

object AppComponents {
  def iconLink(title: Modifier, icon: Modifier, md: Modifier*): ConcreteHtmlTag[dom.html.Anchor] = a(
    href := "javascript:void(0)",
    icon,
    title,
    md
  )

  def dropdownLink(title: String, opened: Var[Boolean]): ConcreteHtmlTag[dom.html.Anchor] = {
    iconLink(title, Rx(Icon.faFw(if (opened()) "caret-down" else "caret-right")), onclick := Callback.onClick(_ ⇒ opened() = !opened.now))
  }

  def dropdown(title: String)(content: ⇒ Modifier): ConcreteHtmlTag[dom.html.Div] = {
    val opened = Var(false)
    div(
      dropdownLink(title, opened),
      Rx[Frag](if (opened()) div(content) else ())
    )
  }

  def modalClose(md: Modifier*)(implicit context: AppContext): Tag = {
    Modal.closeButton(context.locale.close)(md:_*)
  }

  def modalSubmit(md: Modifier*)(implicit context: AppContext): Tag = {
    Button(ButtonStyle.success)(context.locale.submit, Modal.dismiss, md)
  }

  def tabOverride: Modifier = { elem ⇒
    TabOverride.set(elem.asInstanceOf[dom.html.TextArea])
  }
}
