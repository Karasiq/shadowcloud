package com.karasiq.shadowcloud.webapp.context

import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.webapp.api.{AjaxApi, FileApi}
import com.karasiq.shadowcloud.webapp.locales.AppLocale
import com.karasiq.shadowcloud.webapp.utils.TimeFormat

object AppContext {
  implicit val jsExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  def apply(): AppContext = {
    new DefaultAppContext()
  }
}

trait AppContext {
  def api: ShadowCloudApi with FileApi
  def locale: AppLocale
  def timeFormat: TimeFormat
}

class DefaultAppContext extends AppContext {
  val api = AjaxApi
  val locale: AppLocale = AppLocale.default
  val timeFormat: TimeFormat = TimeFormat.forLocale(locale.languageCode)
}
