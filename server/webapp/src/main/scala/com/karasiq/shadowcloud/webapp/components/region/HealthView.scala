package com.karasiq.shadowcloud.webapp.components.region

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.model.utils.HealthStatus
import com.karasiq.shadowcloud.webapp.context.AppContext

object HealthView {
  def apply(healthStatusRx: Rx[HealthStatus])(implicit context: AppContext): HealthView = {
    new HealthView(healthStatusRx)
  }
}

class HealthView(healthStatusRx: Rx[HealthStatus])(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val progressBarRx = Rx {
      val hs = healthStatusRx()
      val progressBarStyle =
        if (!hs.online) ProgressBarStyle.danger
        else if (hs.freeSpace < hs.totalSpace / 2) ProgressBarStyle.warning
        else ProgressBarStyle.success

      div(
        div(b(context.locale.writableSpace, ": ", MemorySize.toString(hs.writableSpace))),
        // div(b(context.locale.freeSpace, ": ", MemorySize.toString(storageHealth.freeSpace))),
        div(b(context.locale.usedSpace, ": ", MemorySize.toString(hs.usedSpace))),
        div(b(context.locale.totalSpace, ": ", MemorySize.toString(hs.totalSpace))),

        ProgressBar.basic(Var(HealthStatus.getUsedPercentage(hs)))
          .renderTag(progressBarStyle, ProgressBarStyle.striped)
      )
    }
    div(progressBarRx, healthStatusRx.map(_.totalSpace == 0).reactiveHide)
  }
}

