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
  def apply()(implicit context: AppContext, rc: RegionContext): RegionsView = {
    new RegionsView()
  }
}

class RegionsView()(implicit context: AppContext, rc: RegionContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val regionViewsRx = rc.regions.fold(Map.empty[RegionId, Tag]) { case (views, report) ⇒
      val filteredMap = views.filterKeys(report.regions.contains)
      val newKeys = report.regions.filterKeys(regionId ⇒ !views.contains(regionId)).map { case (regionId, _) ⇒
        regionId → renderRegion(regionId)
      }
      filteredMap ++ newKeys
    }

    div(
      div(
        h3(context.locale.regions),
        hr,
        renderAddButton(),
        Rx(div(regionViewsRx().values.toSeq)),
        GridSystem.col(6)
      ),
      div(
        h3(context.locale.storages),
        hr,
        // TODO: Storages
        GridSystem.col(6)
      )
    )
  }

  private[this] def renderAddButton() = {
    def doCreate(regionId: RegionId) = {
      context.api.createRegion(regionId, SerializedProps.empty).foreach { _ ⇒
        rc.updateAll()
      }
    }

    def showCreateDialog() = {
      val newRegionNameRx = Var(s"region-${rc.regions.now.regions.size}")
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
      RegionConfigView(regionId)
    }
  }
}

