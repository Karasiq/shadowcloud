package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.AppContext

object RegionSwitcher {
  def apply()(implicit context: AppContext, regionContext: RegionContext): RegionSwitcher = {
    new RegionSwitcher()
  }
}

class RegionSwitcher(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  val regionSelector = RegionSelector()
  val scopeSelector = IndexScopeSelector()

  def renderTag(md: ModifierT*): TagT = {
    Button(ButtonStyle.warning)(AppIcons.region, Bootstrap.nbsp, context.locale.region,
      onclick := Callback.onClick(_ ⇒ this.show()), md)
  }

  def renderModal(): Modal = {
    val content = div(
      GridSystem.mkRow(regionSelector),
      GridSystem.mkRow(scopeSelector)
    )

    Modal(context.locale.region, content, AppComponents.modalClose())
  }


  def show(): Unit = {
    renderModal().show()
  }
}

