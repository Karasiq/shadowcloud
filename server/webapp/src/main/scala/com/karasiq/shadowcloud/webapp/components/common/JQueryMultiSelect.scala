package com.karasiq.shadowcloud.webapp.components.common

import com.karasiq.bootstrap.Bootstrap.default._
import org.scalajs.dom.window
import org.scalajs.jquery.JQuery
import scalaTags.all._

import scala.scalajs.js

//noinspection ScalaUnusedSymbol
@js.native
trait JQueryMultiSelect extends js.Object {
  def multiSelect(options: js.Any = js.native): Unit = js.native
}

object JQueryMultiSelect {
  private implicit def implicitJqueryMultiSelect(jq: JQuery): JQueryMultiSelect = jq.asInstanceOf[JQueryMultiSelect]

  def callFunction(t: Element, name: String): Unit = {
    window.setTimeout(() ⇒ jQuery(t).multiSelect(name), 0)
  }

  def wrap(select: FormSelect): Element = {
    val modifier: Modifier = (t: Element) ⇒
      window.setTimeout(
        { () ⇒
          jQuery(t).multiSelect(
            js.Dynamic.literal(
              afterSelect = (values: js.Array[String]) ⇒ select.selected() = select.selected.now ++ values,
              afterDeselect = (values: js.Array[String]) ⇒ {
                val valuesSet = values.toSet
                select.selected() = select.selected.now.filterNot(valuesSet)
              }
            )
          )
        },
        0
      )

    val rendered = select.renderTag(modifier).render
    select.selected.triggerLater {
      JQueryMultiSelect.callFunction(rendered.querySelector("select"), "refresh")
    }
    rendered
  }
}
