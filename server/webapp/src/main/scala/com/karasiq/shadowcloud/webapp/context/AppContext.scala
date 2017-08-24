package com.karasiq.shadowcloud.webapp.context

import com.karasiq.shadowcloud.webapp.api.AjaxApi
import com.karasiq.shadowcloud.webapp.locales.AppLocale
import com.karasiq.shadowcloud.webapp.utils.TimeFormat

object AppContext {
  def apply(): AppContext = {
    new AppContext()
  }
}

class AppContext {
  val api = AjaxApi
  val locale: AppLocale = AppLocale.default
  val timeFormat: TimeFormat = TimeFormat.forLocale(locale.languageCode)
}
