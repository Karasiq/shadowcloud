package com.karasiq.shadowcloud.webapp.utils

import java.util.Locale

import org.threeten.bp._
import org.threeten.bp.format.{DateTimeFormatter, DateTimeFormatterBuilder, TextStyle}
import org.threeten.bp.temporal.ChronoField

object WebAppUtils {
  private[this] val timestampFormat = new DateTimeFormatterBuilder()
    .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT)
    .appendLiteral(", ")
    .appendValue(ChronoField.DAY_OF_MONTH)
    .appendLiteral('/')
    .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
    .appendLiteral('/')
    .appendValue(ChronoField.YEAR)
    .appendLiteral(", ")
    .append(DateTimeFormatter.ISO_LOCAL_TIME)
    .appendLiteral(" (")
    .appendZoneOrOffsetId
    .appendLiteral(")")
    .toFormatter(Locale.ENGLISH)

  def timestampToString(ts: Long): String = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault)
      .format(timestampFormat)
  }
}
