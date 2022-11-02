package com.karasiq.shadowcloud.webapp.components.common

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.webapp.context.AppContext
import org.scalajs.dom
import rx.Var
import scalaTags.all._

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

@js.native
@JSGlobal("pell")
object PellJS extends js.Object {
  def init(options: js.Dynamic): Element = js.native
}

@js.native
@JSGlobal
class TurndownService(options: js.Dynamic) extends js.Object {
  def turndown(html: String): String = js.native
}

object Pell {
  abstract class Editor(implicit ac: AppContext) extends BootstrapHtmlComponent {
    val submitting = Var(false)
    val html       = Var("")

    def onSubmit(): Unit

    override def renderTag(md: ModifierT*): TagT = {
      val parent    = div(height := 400.px).render
      val container = div("pell".addClass).render
      parent.appendChild(container)

      val options = js.Dynamic.literal(
        element = container,
        onChange = (s: String) ⇒ html() = s
      )

      dom.window.setTimeout(
        () ⇒ {
          PellJS.init(options)
          val editor = container.asInstanceOf[js.Dynamic].content.asInstanceOf[dom.Element]

          html.trigger {
            if (editor.innerHTML != html.now)
              editor.innerHTML = html.now
          }
        },
        0
      )

      div(
        parent,
        Button(ButtonStyle.success, block = true)(
          ac.locale.submit,
          onclick := Callback.onClick(_ ⇒ if (!submitting.now) onSubmit()),
          "disabled".classIf(submitting)
        )
      )
    }
  }

  def apply(f: Editor ⇒ Unit)(implicit ac: AppContext): Editor = new Editor {
    override def onSubmit(): Unit = f(this)
  }

  def toMarkdown(html: String): String =
    new TurndownService(js.Dynamic.literal()).turndown(html)
}
