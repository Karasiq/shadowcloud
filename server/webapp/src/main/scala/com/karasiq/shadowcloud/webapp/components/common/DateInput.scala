package com.karasiq.shadowcloud.webapp.components.common

import java.util.Locale

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom.raw.HTMLInputElement
import org.threeten.bp.LocalDate
import org.threeten.bp.format.DateTimeFormatterBuilder
import org.threeten.bp.temporal.ChronoField
import rx.Var

object DateInput {
  def apply(title: Modifier): DateInput = {
    new DateInput(title)
  }

  private def popup(selectedDate: Var[Option[LocalDate]]): DatePickerPopup = {
    new DatePickerPopup(selectedDate)
  }
}

class DateInput(title: Modifier) extends BootstrapHtmlComponent {
  val selectedDate = Var(None: Option[LocalDate])

  def renderTag(md: ModifierT*) = {
    FormInput.text(title, DateInput.popup(selectedDate), md)
  }
}

private[components] class DatePickerPopup(val selectedDate: Var[Option[LocalDate]]) extends ModifierFactory {
  import org.querki.facades.bootstrap.datepicker._
  import org.querki.jquery._

  protected val dateFormat = "dd.mm.yyyy"
  protected val javaDateFormat = new DateTimeFormatterBuilder()
    .appendValue(ChronoField.DAY_OF_MONTH, 2)
    .appendLiteral('.')
    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
    .appendLiteral('.')
    .appendValue(ChronoField.YEAR, 4)
    .toFormatter(Locale.ENGLISH)

  protected def options = BootstrapDatepickerOptions
    .format(dateFormat)
    .autoclose(true)
    .endDate("0d")
    .immediateUpdates(true)
    .todayHighlight(true)
    .todayBtn(false)
    .clearBtn(true)

  protected def readDate(elem: Element): Unit = {
    val dateString = elem.asInstanceOf[HTMLInputElement].value
    if (dateString.isEmpty) {
      selectedDate() = None
    } else {
      val parsedDate = LocalDate.parse(dateString, javaDateFormat)
      selectedDate() = Some(parsedDate)
    }
  }

  protected def writeDate(elem: Element, value: Option[LocalDate]): Unit = value match {
    case Some(date) ⇒
      elem.asInstanceOf[HTMLInputElement].value = date.format(javaDateFormat)

    case None ⇒
      elem.asInstanceOf[HTMLInputElement].value = ""
  }

  def createModifier = (elem: Element) ⇒ {
    $(elem).datepicker(options)
    $(elem).on("changeDate", (_: JQueryEventObject) ⇒ readDate(elem))
    selectedDate.foreach(writeDate(elem, _))
  }
}

