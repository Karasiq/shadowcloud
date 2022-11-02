package com.karasiq.shadowcloud.webapp.locales.impl

import com.karasiq.shadowcloud.webapp.locales.AppLocale

private[locales] object Locales {
  val all                = Vector(English, Russian)
  val default: AppLocale = all.head
}
