package com.karasiq.shadowcloud.webapp.context

import com.karasiq.shadowcloud.webapp.locales.AppLocale

object AppContext {
  def apply(): AppContext = {
    new AppContext()
  }
}

class AppContext {
  val locale: AppLocale = AppLocale.default
}
