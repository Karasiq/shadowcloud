package com.karasiq.shadowcloud.webapp.context

import scala.language.implicitConversions
import scalacss.internal.mutable.StyleSheet

import com.karasiq.bootstrap.Bootstrap
import com.karasiq.shadowcloud.api.{SCApiMeta, ShadowCloudApi}
import com.karasiq.shadowcloud.webapp.api.{AjaxApi, FileApi}
import com.karasiq.shadowcloud.webapp.locales.AppLocale
import com.karasiq.shadowcloud.webapp.styles.FolderTreeStyles
import com.karasiq.shadowcloud.webapp.utils.TimeFormat
import com.karasiq.taboverridejs.TabOverride

object AppContext {
  implicit val JsExecutionContext = scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
  val BootstrapContext = Bootstrap.default
  val CssSettings = scalacss.devOrProdDefaults

  def apply(): AppContext = {
    new DefaultAppContext()
  }

  def applyStyles()(implicit appContext: AppContext): Unit = {
    import org.scalajs.dom
    import org.scalajs.dom.raw.HTMLStyleElement

    TabOverride.tabSize(2)
    appContext.styles.foreach { stylesheet ⇒
      import AppContext.CssSettings._
      dom.document.head.appendChild(stylesheet.render[HTMLStyleElement])
    }
  }

  object Implicits {
    import BootstrapContext._
    import scalaTags.all._

    implicit def convertStyleToModifier(style: CssSettings.StyleA): Modifier = {
      (style.className +: style.addClassNames).map(className ⇒ className.value.addClass)
    }
  }
}

trait AppContext {
  type ApiT = ShadowCloudApi with FileApi with SCApiMeta
  val api: ApiT
  val locale: AppLocale
  val timeFormat: TimeFormat
  val styles: Seq[StyleSheet.Base]
}

class DefaultAppContext extends AppContext {
  val api = AjaxApi
  val locale: AppLocale = AppLocale.default
  val timeFormat: TimeFormat = TimeFormat.forLocale(locale.languageCode)
  val styles = Seq(FolderTreeStyles)
}
