package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.model.utils.FileAvailability
import com.karasiq.shadowcloud.model.{File, StorageId}
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons, Toastr}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.utils.RxWithUpdate
import rx._
import scalaTags.all._

object FileAvailabilityView {
  def apply(file: File)(implicit context: AppContext, folderContext: FolderContext): FileAvailabilityView = {
    new FileAvailabilityView(file)
  }

  private def getAvailabilityRx(file: File)(implicit context: AppContext, folderContext: FolderContext) = {
    RxWithUpdate(FileAvailability.empty(file))(_ ⇒ context.api.getFileAvailability(folderContext.regionId, file, folderContext.scope.now))
  }
}

class FileAvailabilityView(file: File)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  private[this] lazy val availabilityRx = FileAvailabilityView.getAvailabilityRx(file)

  def renderTag(md: ModifierT*): TagT = {
    div(
      div(renderRepairLink()),
      Rx(renderContent(availabilityRx.toRx(), md:_*))
    )
  }

  def renderContent(fileAvailability: FileAvailability, md: ModifierT*): TagT = {
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
      div(b(context.locale.total, ": ", f"${fileAvailability.totalPercentage}%.2f%%")),
      for ((storageId, percentage) ← sortedPercentages)
        yield div(icon(percentage), storageId, f" ($percentage%.2f%%)", textStyle(percentage))
    )(md:_*)
  }

  def renderRepairLink(): TagT = {
    val repairing = Var(false)
    def showDialog(): Unit = {
      def doRepair(storages: Seq[StorageId]) = {
        if (!repairing.now) {
          repairing() = true
          val future = context.api.repairFile(folderContext.regionId, file, storages, folderContext.scope.now)
          future.onComplete { _ ⇒
            repairing() = false
            availabilityRx.update()
            Toastr.success(s"File replication finished: ${file.path}")
          }
        }
      }

      context.api.getRegion(folderContext.regionId).foreach { status ⇒
        val storagesSelector = FormInput.simpleMultipleSelect(context.locale.storages, status.storages.toSeq:_*)
        Modal(context.locale.repairFile)
          .withBody(Form(storagesSelector))
          .withButtons(
            AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ doRepair(storagesSelector.selected.now))),
            AppComponents.modalClose()
          )
          .show()
      }
    }
    AppComponents.iconLink(context.locale.repairFile, AppIcons.repair, Bootstrap.textStyle.muted.className.classIf(repairing), onclick := Callback.onClick(_ ⇒ if (!repairing.now) showDialog()))
  }
}

