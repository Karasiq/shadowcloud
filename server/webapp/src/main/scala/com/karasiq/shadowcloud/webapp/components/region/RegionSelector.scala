package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Var

import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.webapp.context.AppContext

object RegionSelector {
  def apply()(implicit context: AppContext, regionContext: RegionContext): RegionSelector = {
    new RegionSelector()
  }
}

class RegionSelector(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  val regionIdRx = Var(None: Option[RegionId])

  def renderTag(md: ModifierT*): TagT = {
    val options = regionContext.regions.map { state ⇒
      state.regions
        .filterNot(_._2.suspended)
        .keys.toSeq
        .sorted
        .map(id ⇒ FormSelectOption(id, id))
    }

    val select = FormInput.select(context.locale.regionId, options)
    select.selected.map(_.headOption).foreach(this.regionIdRx.update)
    Form(select)
  }
}
