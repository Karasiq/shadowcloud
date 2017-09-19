package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Rx

import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.context.AppContext

object RegionsView {
  def apply()(implicit context: AppContext, rc: RegionContext): RegionsView = {
    new RegionsView()
  }
}

class RegionsView()(implicit context: AppContext, rc: RegionContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    def renderRegion(regionId: RegionId) = {
      AppComponents.dropdown(regionId) {
        RegionConfigView(regionId)
      }
    }

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
}

