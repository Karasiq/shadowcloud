package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Var

import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.model.utils.RegionStateReport
import com.karasiq.shadowcloud.webapp.context.AppContext

object RegionSelector {
  def apply()(implicit context: AppContext, regionContext: RegionContext): RegionSelector = {
    new RegionSelector()
  }

  private def getRegionIds(state: RegionStateReport): Seq[RegionId] = {
    state.regions
      .filterNot(_._2.suspended)
      .keys.toSeq
      .sorted
  }
}

class RegionSelector(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  val selectedRegion = Var(RegionSelector.getRegionIds(regionContext.regions.now).headOption)
  private[this] val selectField = renderSelectField()

  selectedRegion.triggerLater {
    if (selectedRegion.now.isEmpty) reset()
  }

  regionContext.regions.triggerLater {
    if (selectedRegion.now.isEmpty) reset()
  }

  def reset(): Unit = {
    val state = regionContext.regions.now
    selectedRegion() = RegionSelector.getRegionIds(state).headOption
  }

  def renderTag(md: ModifierT*): TagT = {
    Form(selectField)
  }

  private[this] def renderSelectField(): FormSelect = {
    val options = regionContext.regions.map { state ⇒
      RegionSelector.getRegionIds(state)
        .map(id ⇒ FormSelectOption(id, id))
    }

    val select = FormInput.select(context.locale.regionId, options)
    select.selected.foreach(seq ⇒ selectedRegion() = seq.headOption)
    selectedRegion.foreach(opt ⇒ select.selected() = opt.toSeq)
    select
  }
}
