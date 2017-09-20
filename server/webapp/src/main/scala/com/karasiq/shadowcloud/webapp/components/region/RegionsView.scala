package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext

object RegionsView {
  def apply()(implicit context: AppContext, regionContext: RegionContext): RegionsView = {
    new RegionsView
  }

  private def newRegionName()(implicit rc: RegionContext): RegionId = {
    s"region-${rc.regions.now.regions.size}"
  }
}

class RegionsView(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val regionViewsRx = regionContext.regions.fold(Map.empty[RegionId, Tag]) { case (views, report) ⇒
      val newMap = report.regions.map { case (regionId, _) ⇒
        regionId → views.getOrElse(regionId, renderRegion(regionId))
      }
      newMap
    }

    div(
      renderAddButton(),
      Rx(div(regionViewsRx().toSeq.sortBy(_._1).map(_._2)))
    )
  }

  private[this] def renderAddButton() = {
    def doCreate(regionId: RegionId) = {
      context.api.createRegion(regionId, SerializedProps.empty).foreach { _ ⇒
        regionContext.updateAll()
      }
    }

    def showCreateDialog() = {
      val newRegionNameRx = Var(RegionsView.newRegionName())
      Modal()
        .withTitle(context.locale.createRegion)
        .withBody(Form(FormInput.text(context.locale.regionId, newRegionNameRx.reactiveInput)))
        .withButtons(
          AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ doCreate(newRegionNameRx.now))),
          AppComponents.modalClose()
        )
        .show()
    }

    Button(ButtonStyle.primary, ButtonSize.small, block = true)(
      AppIcons.create, Bootstrap.nbsp, context.locale.createRegion,
      onclick := Callback.onClick(_ ⇒ showCreateDialog())
    )
  }

  private[this] def renderRegion(regionId: RegionId) = {
    AppComponents.dropdown(regionId) {
      Bootstrap.well(RegionConfigView(regionId))
    }
  }
}

