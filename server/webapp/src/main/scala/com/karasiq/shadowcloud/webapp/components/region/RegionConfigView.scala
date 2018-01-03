package com.karasiq.shadowcloud.webapp.components.region

import scala.concurrent.Future

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import akka.util.ByteString
import rx.{Rx, Var}

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.model.utils.{GCReport, RegionHealth, SyncReport}
import com.karasiq.shadowcloud.model.utils.RegionStateReport.RegionStatus
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.RxWithKey

object RegionConfigView {
  def apply(regionId: RegionId)(implicit context: AppContext, regionContext: RegionContext): RegionConfigView = {
    new RegionConfigView(regionId)
  }
}

class RegionConfigView(regionId: RegionId)(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  private[this] lazy val regionRx = regionContext.region(regionId)
  private[this] lazy val regionHealthRx = RxWithKey(regionRx, RegionHealth.empty)(r ⇒ context.api.getRegionHealth(r.regionId))
  private[this] lazy val compactStarted = Var(false)
  private[this] lazy val compactReportRx = Var(Map.empty: Map[StorageId, SyncReport])
  private[this] lazy val gcStarted = Var(false)
  private[this] lazy val gcAnalysed = Var(false)
  private[this] lazy val gcReportRx = Var(None: Option[GCReport])

  def renderTag(md: ModifierT*): TagT = {
    div(regionRx.map { regionStatus ⇒
      div(
        if (!regionStatus.suspended) {
          Seq(
            renderRegionHealth(),
            renderCompactButton(),
            renderGCButton(),
            hr
          )
        } else (),
        renderStateButtons(regionStatus),
        renderConfigField(regionStatus),
        hr,
        renderStoragesRegistration(regionStatus)
      )
    })
  }

  private[this] def renderRegionHealth() = {
    HealthView(regionHealthRx.toRx)
  }

  private[this] def renderGCButton() = {
    def doGC(delete: Boolean) = {
      val future = context.api.collectGarbage(regionId, delete)
      future.onComplete(_ ⇒ gcStarted() = false)
      future.foreach { report ⇒
        gcAnalysed() = !delete
        gcReportRx() = Some(report)
      }
    }

    div(
      Rx {
        val buttonStyle = if (gcAnalysed()) ButtonStyle.danger else ButtonStyle.warning
        Button(buttonStyle, ButtonSize.extraSmall)(
          AppIcons.delete, context.locale.collectGarbage, "disabled".classIf(gcStarted),
          onclick := Callback.onClick(_ ⇒ if (!gcStarted.now) doGC(delete = gcAnalysed.now))
        )
      },
      div(gcReportRx.map[Frag] {
        case Some(report) ⇒
          div(Alert(AlertStyle.warning, report.toString))

        case None ⇒
          Bootstrap.noContent
      })
    )
  }

  private[this] def renderCompactButton() = {
    def doCompact() = {
      val future = context.api.compactIndexes(regionId)
      future.onComplete(_ ⇒ compactStarted() = false)
      future.foreach(compactReportRx.update)
    }

    def renderReports(reports: Map[StorageId, SyncReport]): Frag = {
      if (reports.nonEmpty)
        div(Alert(AlertStyle.success, reports.toSeq.map(rp ⇒ div(b(rp._1), ": ", rp._2.toString))))
      else
        Bootstrap.noContent
    }

    div(
      Button(ButtonStyle.danger, ButtonSize.extraSmall)(AppIcons.compress, context.locale.compactIndex, "disabled".classIf(compactStarted),
        onclick := Callback.onClick(_ ⇒ if (!compactStarted.now) doCompact())),
      div(compactReportRx.map(reports ⇒ renderReports(reports)))
    )
  }

  private[this] def renderStateButtons(regionStatus: RegionStatus) = {
    def doSuspend() = {
      context.api.suspendRegion(regionId)
        .foreach(_ ⇒ regionContext.updateRegion(regionId))
    }

    def doResume() = {
      context.api.resumeRegion(regionId)
        .foreach(_ ⇒ regionContext.updateRegion(regionId))
    }

    def doDelete() = {
      context.api.deleteRegion(regionId)
        .foreach(_ ⇒ regionContext.updateAll())
    }

    val suspendButton = if (regionStatus.suspended)
      Button(ButtonStyle.success, ButtonSize.extraSmall)(AppIcons.resume, context.locale.resume, onclick := Callback.onClick(_ ⇒ doResume()))
    else
      Button(ButtonStyle.warning, ButtonSize.extraSmall)(AppIcons.suspend, context.locale.suspend, onclick := Callback.onClick(_ ⇒ doSuspend()))

    val deleteButton = Button(ButtonStyle.danger, ButtonSize.extraSmall)(AppIcons.delete, context.locale.delete, onclick := Callback.onClick(_ ⇒ doDelete()))

    ButtonGroup(ButtonGroupSize.extraSmall, suspendButton, deleteButton)
  }

  private[this] def renderConfigField(regionStatus: RegionStatus) = {
    def renderConfigForm() = {
      val changed = Var(false)
      val newConfigRx = Var(regionStatus.regionConfig.data.utf8String)
      newConfigRx.triggerLater(changed() = true)
      
      Form(
        FormInput.textArea((), rows := 20, newConfigRx.reactiveInput, AppComponents.tabOverride),
        Form.submit(context.locale.submit)(changed.reactiveShow, onclick := Callback.onClick { _ ⇒
          val newConfig = SerializedProps(regionStatus.regionConfig.format, ByteString(newConfigRx.now))
          context.api.createRegion(regionId, newConfig)
            .foreach(_ ⇒ regionContext.updateRegion(regionId))
        })
      )
    }

    AppComponents.dropdown(context.locale.config)(renderConfigForm())
  }

  private[this] def renderStoragesRegistration(regionStatus: RegionStatus) = {
    def updateStorageList(newIdSet: Set[StorageId]) = {
      val currentIdSet = regionStatus.storages
      val toRegister = newIdSet -- currentIdSet
      val toUnregister = currentIdSet -- newIdSet
      for {
        _ ← Future.sequence(toUnregister.map(context.api.unregisterStorage(regionId, _)))
        _ ← Future.sequence(toRegister.map(context.api.registerStorage(regionId, _)))
      } regionContext.updateAll()
    }

    def renderAddButton() = {
      def showAddDialog(): Unit = {
        val allIds = regionContext.regions.now.storages.keys.toSeq.sorted
        val idSelect = FormInput.multipleSelect(context.locale.storages, allIds.map(id ⇒ FormSelectOption(id, id)))
        idSelect.selected() = regionStatus.storages.toSeq
        Modal()
          .withTitle(context.locale.registerStorage)
          .withBody(Form(idSelect))
          .withButtons(
            AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ updateStorageList(idSelect.selected.now.toSet))),
            AppComponents.modalClose()
          )
          .show()
      }

      Button(ButtonStyle.primary, ButtonSize.extraSmall)(AppIcons.register, context.locale.registerStorage, onclick := Callback.onClick(_ ⇒ showAddDialog()))
    }

    def renderStorage(storageId: StorageId) = {
      div(
        b(storageId),
        Bootstrap.textStyle.success
      )
    }

    val storagesSeq = regionStatus.storages.toSeq.sorted
    div(
      storagesSeq.map(renderStorage),
      renderAddButton()
    )
  }
}

