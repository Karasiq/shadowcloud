package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx._
import async._

import com.karasiq.shadowcloud.model.{File, RegionId}
import com.karasiq.shadowcloud.model.utils.FileAvailability
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.context.AppContext
import AppContext.JsExecutionContext

object FileAvailabilityView {
  def apply(fileAvailability: FileAvailability)(implicit context: AppContext): FileAvailabilityView = {
    new FileAvailabilityView(fileAvailability)
  }

  def forFile(regionId: RegionId, file: File)(implicit context: AppContext): BootstrapHtmlComponent = {
    new BootstrapHtmlComponent {
      private[this] lazy val availabilityRx = {
        val fullFile = context.api.getFileById(regionId, file.path, file.id)
        val availability = fullFile.flatMap(context.api.getFileAvailability(regionId, _))
        availability.toRx(FileAvailability(file, Map.empty))
      }

      def renderTag(md: ModifierT*) = {
        div(availabilityRx.map(FileAvailabilityView(_)))
      }
    }
  }
}

class FileAvailabilityView(fileAvailability: FileAvailability)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    def icon(percentage: Double) = percentage match {
      case 100 ⇒
        AppIcons.fullyAvailable

      case _ ⇒
        AppIcons.partiallyAvailable
    }

    def textStyle(percentage: Double): ModifierT = percentage match {
      case 100 ⇒
        Bootstrap.textStyle.success

      case p if p > 50 ⇒
        Bootstrap.textStyle.warning

      case _ ⇒
        Bootstrap.textStyle.danger
    }

    val sortedPercentages = fileAvailability.percentagesByStorage.toSeq.sortBy(_._2)(Ordering[Double].reverse)
    div(
      for ((storageId, percentage) ← sortedPercentages)
        yield div(icon(percentage), Bootstrap.nbsp, storageId, f" (%.2f$percentage%%)", textStyle(percentage))
    )
  }
}

