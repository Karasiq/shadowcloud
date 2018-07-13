package com.karasiq.shadowcloud.webapp.components.common

import rx.Var

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.webapp.context.AppContext

object TextEditor {
  def apply(_onSubmit: TextEditor ⇒ Unit)(implicit context: AppContext): TextEditor = {
    new TextEditor {
      def onSubmit(): Unit = _onSubmit(this)
    }
  }
}

abstract class TextEditor(implicit context: AppContext) extends BootstrapHtmlComponent {
  val value = Var("")
  val submitting = Var(false)

  def onSubmit(): Unit

  def renderTag(md: ModifierT*): TagT = {
    Form(
      FormInput.textArea((), rows := 10, value.reactiveInput, AppComponents.tabOverride),
      Form.submit(context.locale.submit, ButtonStyle.success, "btn-block".addClass, "disabled".classIf(submitting)),
      onsubmit := Callback.onSubmit(_ ⇒ if (!submitting.now) onSubmit())
    )
  }
}

