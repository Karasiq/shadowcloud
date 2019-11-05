package com.karasiq.shadowcloud.webapp.components.common

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.utils.LocalStorage
import rx.Var
import scalaTags.all._

object TextEditor {
  def apply(_onSubmit: TextEditor ⇒ Unit)(implicit context: AppContext): TextEditor = {
    new TextEditor {
      def onSubmit(): Unit = _onSubmit(this)
    }
  }

  def memoized(key: String)(_onSubmit: TextEditor ⇒ Unit)(implicit context: AppContext): TextEditor = {
    new TextEditor {
      override val value: Var[String] = LocalStorage.memoize(key)
      def onSubmit(): Unit            = _onSubmit(this)
    }
  }
}

sealed abstract class TextEditor(implicit context: AppContext) extends BootstrapHtmlComponent {
  val value      = Var("")
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
