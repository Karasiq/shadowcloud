package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.webapp.components.keys.{KeysContext, KeysView}
import com.karasiq.shadowcloud.webapp.context.AppContext

object RegionsStoragesPanel {
  def apply()(implicit context: AppContext, regionContext: RegionContext, keysContext: KeysContext): RegionsStoragesPanel = {
    new RegionsStoragesPanel
  }
}

class RegionsStoragesPanel(implicit context: AppContext, regionContext: RegionContext, keysContext: KeysContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    GridSystem.row(
      div(
        h3(context.locale.regions),
        hr,
        RegionsView(),
        GridSystem.col.responsive(12, 12, 6, 4)
      ),
      div(
        h3(context.locale.storages),
        hr,
        StoragesView(),
        GridSystem.col.responsive(12, 12, 6, 4)
      ),
      div(
        h3(context.locale.keys),
        hr,
        KeysView(),
        GridSystem.col.responsive(12, 12, 6, 4)
      )
    )
  }
}
