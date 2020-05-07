package com.karasiq.shadowcloud.webapp.components.region

import akka.util.ByteString
import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.utils.RegionStateReport.RegionStatus
import com.karasiq.shadowcloud.model.utils.{GCReport, RegionHealth, SyncReport}
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.utils.RxWithKey
import rx._
import scalaTags.all._

import scala.concurrent.Future

object RegionConfigView {
  def apply(regionId: RegionId)(implicit context: AppContext, regionContext: RegionContext): RegionConfigView = {
    new RegionConfigView(regionId)
  }

  private def toRegionConfig(regionStatus: RegionStatus, newConfig: String): SerializedProps = {
    val format = Option(regionStatus.regionConfig.format)
      .filter(_.nonEmpty)
      .getOrElse(SerializedProps.DefaultFormat)

    SerializedProps(format, ByteString(newConfig))
  }
}

class RegionConfigView(regionId: RegionId)(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  private[this] lazy val regionStatus = regionContext.region(regionId)

  private[this] object state {
    val syncStarted    = Var(false)
    val compactStarted = Var(false)
    val repairStarted  = Var(false)
    val gcStarted      = Var(false)
    val gcAnalysed     = Var(false)
    val gcReport       = Var(None: Option[GCReport])
    val syncReport     = Var(Map.empty[StorageId, SyncReport])
    val compactReport  = Var(Map.empty[StorageId, SyncReport])
    val healthRx       = RxWithKey.static(regionId, RegionHealth.empty)(context.api.getRegionHealth)
  }

  import state._

  // Hack to bypass an initialization lag
  def updateHealth(): Unit = {
    healthRx.update()
    org.scalajs.dom.window.setTimeout(() => healthRx.update(), 100)
    org.scalajs.dom.window.setTimeout(() => healthRx.update(), 500)
    org.scalajs.dom.window.setTimeout(() => healthRx.update(), 1500)
  }

  syncReport.triggerLater(healthRx.update())
  compactReport.triggerLater(healthRx.update())
  gcReport.triggerLater(healthRx.update())

  def renderTag(md: ModifierT*): TagT = {
    updateHealth()

    div(regionStatus.map { regionStatus ⇒
      div(
        if (!regionStatus.suspended) {
          Seq(
            div(HealthView(healthRx.toRx), onclick := Callback.onClick(_ => updateHealth())),
            renderCompactButton(),
            renderGCButton(),
            renderRepairButton(),
            renderSyncButton(),
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

  private[this] def renderGCButton() = {
    def startGC(delete: Boolean) = {
      gcStarted() = true
      val future = context.api.collectGarbage(regionId, delete)
      future.onComplete(_ ⇒ gcStarted() = false)
      future.foreach { report ⇒
        gcAnalysed() = !delete
        gcReport() = Some(report)
      }
    }

    val gcBlocked = Rx(gcStarted() || compactStarted() || repairStarted())
    div(
      Rx {
        val buttonStyle = if (gcAnalysed()) ButtonStyle.danger else ButtonStyle.warning
        Button(buttonStyle, ButtonSize.extraSmall)(
          AppIcons.delete,
          context.locale.collectGarbage,
          "disabled".classIf(gcBlocked),
          onclick := Callback.onClick(_ ⇒ if (!gcBlocked.now) startGC(delete = gcAnalysed.now))
        )
      },
      div(gcReport.map[Frag] {
        case Some(report) ⇒ AppComponents.closeableAlert(AlertStyle.warning, () => gcReport() = None, report.toString)
        case None         ⇒ Bootstrap.noContent
      })
    )
  }

  private[this] def renderSyncReports(reportsVar: Var[Map[StorageId, SyncReport]]) =
    div(reportsVar.map { reports =>
      if (reports.nonEmpty)
        div(
          AppComponents.closeableAlert(
            AlertStyle.success,
            () => reportsVar() = Map.empty,
            for ((storageId, report) ← reports.toSeq) yield div(b(storageId), ": ", report.toString)
          )
        )
      else
        Bootstrap.noContent
    })

  private[this] def renderCompactButton() = {
    def startCompact() = {
      compactStarted() = true
      val future = context.api.compactIndexes(regionId)
      future.onComplete(_ ⇒ compactStarted() = false)
      future.foreach(compactReport() = _)
    }

    div(
      Button(ButtonStyle.danger, ButtonSize.extraSmall)(
        AppIcons.compress,
        context.locale.compactIndex,
        "disabled".classIf(compactStarted),
        onclick := Callback.onClick(_ ⇒ if (!compactStarted.now) startCompact())
      ),
      renderSyncReports(compactReport)
    )
  }

  def renderRepairButton(): TagT = {
    def showDialog(): Unit = {
      def doRepair(storages: Seq[StorageId]) = {
        if (!repairStarted.now) {
          repairStarted() = true
          val future = context.api.repairRegion(regionId, storages)
          future.onComplete(_ ⇒ repairStarted() = false)
        }
      }

      context.api.getRegion(regionId).foreach { status ⇒
        val storagesSelector = FormInput.simpleMultipleSelect(context.locale.storages, status.storages.toSeq: _*)
        Modal(context.locale.repairFile)
          .withBody(Form(storagesSelector))
          .withButtons(
            AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ doRepair(storagesSelector.selected.now))),
            AppComponents.modalClose()
          )
          .show()
      }
    }

    Button(ButtonStyle.success, ButtonSize.extraSmall)(
      AppIcons.repair,
      context.locale.repairRegion,
      "disabled".classIf(repairStarted),
      onclick := Callback.onClick(_ ⇒ if (!repairStarted.now) showDialog())
    )
  }

  def renderSyncButton(): TagT = {
    def startSync(): Unit = {
      syncStarted() = true
      val future = context.api.synchronizeRegion(regionId)
      future.onComplete(_ ⇒ syncStarted() = false)
      future.foreach(syncReport() = _)
    }

    div(
      Button(ButtonStyle.primary, ButtonSize.extraSmall)(
        AppIcons.refresh,
        context.locale.synchronize,
        "disabled".classIf(syncStarted),
        onclick := Callback.onClick(_ ⇒ if (!syncStarted.now) startSync())
      ),
      renderSyncReports(syncReport)
    )
  }

  private[this] def renderStateButtons(regionStatus: RegionStatus) = {
    def doSuspend() = context.api.suspendRegion(regionId).foreach(_ ⇒ regionContext.updateRegion(regionId))
    def doResume() = context.api.resumeRegion(regionId).foreach { _ =>
      regionContext.updateRegion(regionId)
      updateHealth()
    }
    def doDelete() = context.api.deleteRegion(regionId).foreach(_ ⇒ regionContext.updateAll())

    val suspendResumeButton =
      if (regionStatus.suspended)
        Button(ButtonStyle.success, ButtonSize.extraSmall)(AppIcons.resume, context.locale.resume, onclick := Callback.onClick(_ ⇒ doResume()))
      else
        Button(ButtonStyle.warning, ButtonSize.extraSmall)(AppIcons.suspend, context.locale.suspend, onclick := Callback.onClick(_ ⇒ doSuspend()))

    val deleteButton =
      Button(ButtonStyle.danger, ButtonSize.extraSmall)(AppIcons.delete, context.locale.delete, onclick := Callback.onClick(_ ⇒ doDelete()))

    ButtonGroup(ButtonGroupSize.extraSmall, suspendResumeButton, deleteButton)
  }

  private[this] def renderConfigField(regionStatus: RegionStatus) = {
    val defaultConfig =
      """storage-selector="com.karasiq.shadowcloud.storage.replication.selectors.SimpleStorageSelector"
        |data-replication-factor=1
        |index-replication-factor=0
        |garbage-collector {
        |    auto-delete=false
        |    keep-file-revisions=5
        |    keep-recent-files="30d"
        |    run-on-low-space="100M"
        |}
      """.stripMargin

    def renderConfigForm() = {
      val changed     = Var(false)
      val newConfigRx = Var(regionStatus.regionConfig.data.utf8String)
      newConfigRx.triggerLater(changed() = true)

      Form(
        FormInput.textArea((), rows := 10, newConfigRx.reactiveInput, placeholder := defaultConfig, AppComponents.tabOverride),
        Form.submit(context.locale.submit, changed.reactiveShow),
        onsubmit := Callback.onSubmit { _ ⇒
          val newConfig = RegionConfigView.toRegionConfig(regionStatus, newConfigRx.now)
          context.api.createRegion(regionId, newConfig).foreach(_ ⇒ regionContext.updateRegion(regionId))
        }
      )
    }

    AppComponents.dropdown(context.locale.config)(renderConfigForm())
  }

  private[this] def renderStoragesRegistration(regionStatus: RegionStatus) = {
    def updateStorageList(newIdSet: Set[StorageId]) = {
      val currentIdSet = regionStatus.storages
      val toRegister   = newIdSet -- currentIdSet
      val toUnregister = currentIdSet -- newIdSet
      for {
        _ ← Future.sequence(toUnregister.map(context.api.unregisterStorage(regionId, _)))
        _ ← Future.sequence(toRegister.map(context.api.registerStorage(regionId, _)))
      } {
        regionContext.updateAll()
        updateHealth()
      }
    }

    def renderAddButton() = {
      def showAddDialog(): Unit = {
        val allIds   = regionContext.regions.now.storages.keys.toSeq.sorted
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

      Button(ButtonStyle.primary, ButtonSize.extraSmall)(
        AppIcons.register,
        context.locale.registerStorage,
        onclick := Callback.onClick(_ ⇒ showAddDialog())
      )
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
