package com.karasiq.shadowcloud.webapp.components.common

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.utils.Blobs
import com.karasiq.taboverridejs.TabOverride
import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.html.TextArea
import rx.{Rx, Var}
import scalaTags.all._
import scalatags.JsDom
import scalatags.JsDom.all.{a, href, onclick}

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
    Modal.closeButton(context.locale.close)(md: _*)
  }

  def modalSubmit(md: Modifier*)(implicit context: AppContext): Tag = {
    Button(ButtonStyle.success)(context.locale.submit, Modal.dismiss, md)
  }

  def tabOverride: Modifier = { elem ⇒
    TabOverride.set(elem.asInstanceOf[dom.html.TextArea])
  }

  def closeableAlert(style: AlertStyle, onClose: () => Unit, md: Modifier*): Tag =
    div(new UniversalAlert(style) {
      override def closeButton: JsDom.all.Tag =
        super.closeButton(onclick := Callback.onClick(_ => onClose()))
    }.renderTag(md: _*))

  def exportDialog(title: String, fileName: String, content: String, contentType: String = "application/json")(implicit context: AppContext): Modal = {
    def download(): Unit =
      Blobs.saveBlob(Blobs.fromString(content, contentType), fileName)

    Modal()
      .withTitle(title)
      .withBody(FormInput.textArea("", content, rows := 30, readonly, onclick := { (e: MouseEvent) ⇒
        val textArea = e.target.asInstanceOf[TextArea]
        textArea.focus()
        textArea.select()
      }))
      .withButtons(
        Button(ButtonStyle.success)(context.locale.downloadFile, onclick := Callback.onClick(_ ⇒ download())),
        AppComponents.modalClose()
      )
  }

  def importDialog(title: String)(submit: String => Unit)(implicit context: AppContext): Modal = {
    val result = Var("")
    Modal()
      .withTitle(title)
      .withBody(Form(FormInput.textArea("", rows := 30, result.reactiveInput)))
      .withButtons(
        AppComponents.modalSubmit(result.map(_.isEmpty).reactiveHide, onclick := Callback.onClick { _ ⇒
          submit(result.now)
          result.kill()
        }),
        AppComponents.modalClose()
      )
  }
}
