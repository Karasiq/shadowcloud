package com.karasiq.shadowcloud.webapp.utils

trait TimeFormat {
  def timestamp(ts: Long): String
}

object TimeFormat {
  def forLocale(locale: String): TimeFormat = {
    new DefaultTimeFormat(locale)
  }
}

private[utils] class DefaultTimeFormat(locale: String) extends TimeFormat {
  import java.util.Locale

  import org.threeten.bp.{Instant, ZonedDateTime, ZoneId}
  import org.threeten.bp.format.{DateTimeFormatterBuilder, TextStyle}
  import org.threeten.bp.temporal.ChronoField

  private[this] val timestampFormat = new DateTimeFormatterBuilder()
    // .appendText(ChronoField.DAY_OF_WEEK, TextStyle.SHORT)
    // .appendLiteral(", ")
    .appendValue(ChronoField.DAY_OF_MONTH)
    .appendLiteral(' ')
    .appendText(ChronoField.MONTH_OF_YEAR, TextStyle.SHORT)
    .appendLiteral(' ')
    .appendValue(ChronoField.YEAR)
    .appendLiteral(" ")
    .appendValue(ChronoField.HOUR_OF_DAY)
    .appendLiteral(':')
    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
    //.append(DateTimeFormatter.ISO_LOCAL_TIME)
    // .appendLiteral(" (")
    // .appendZoneOrOffsetId
    // .appendLiteral(")")
    .toFormatter(Locale.forLanguageTag(locale))

  def timestamp(ts: Long) = {
    ZonedDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault)
      .format(timestampFormat)
  }
}
