package com.karasiq.shadowcloud.webapp.components.region

import akka.util.ByteString
import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.StorageId
import com.karasiq.shadowcloud.model.utils.RegionStateReport.StorageStatus
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import rx.Var
import rx.async._
import scalaTags.all._

import scala.concurrent.Future

object StorageConfigView {
  def apply(storageId: StorageId)(implicit context: AppContext, regionContext: RegionContext): StorageConfigView = {
    new StorageConfigView(storageId)
  }
}

class StorageConfigView(storageId: StorageId)(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  private[this] lazy val storageStatus = regionContext.storage(storageId)

  def renderTag(md: ModifierT*): TagT = {
    div(storageStatus.map { storageStatus ⇒
      div(
        if (!storageStatus.suspended) Seq(renderStorageHealth(), hr) else (),
        renderStateButtons(storageStatus),
        renderConfigField(storageStatus),
        hr,
        renderRegionsRegistration(storageStatus)
      )
    })
  }

  private[this] def renderStorageHealth() = {
    val storageHealth = context.api.getStorageHealth(storageId).toRx(StorageHealth.empty)
    HealthView(storageHealth)
  }

  private[this] def renderStateButtons(storageStatus: StorageStatus) = {
    def doSuspend() = {
      context.api
        .suspendStorage(storageId)
        .foreach(_ ⇒ regionContext.updateStorage(storageId))
    }

    def doResume() = {
      context.api
        .resumeStorage(storageId)
        .foreach(_ ⇒ regionContext.updateStorage(storageId))
    }

    def doDelete() = {
      context.api
        .deleteStorage(storageId)
        .foreach(_ ⇒ regionContext.updateAll())
    }

    def doReset() = {
      context.api
        .resetStorageSessions(storageId)
        .foreach(_ ⇒ regionContext.updateStorage(storageId))
    }

    val suspendButton =
      if (storageStatus.suspended)
        Button(ButtonStyle.success, ButtonSize.extraSmall)(AppIcons.resume, context.locale.resume, onclick := Callback.onClick(_ ⇒ doResume()))
      else
        Button(ButtonStyle.warning, ButtonSize.extraSmall)(AppIcons.suspend, context.locale.suspend, onclick := Callback.onClick(_ ⇒ doSuspend()))

    val deleteButton =
      Button(ButtonStyle.danger, ButtonSize.extraSmall)(AppIcons.delete, context.locale.delete, onclick := Callback.onClick(_ ⇒ doDelete()))

    val resetButton =
      Button(ButtonStyle.info, ButtonSize.extraSmall)(AppIcons.refresh, context.locale.reset, onclick := Callback.onClick(_ ⇒ doReset()))

    ButtonGroup(ButtonGroupSize.extraSmall, suspendButton, deleteButton, resetButton)
  }

  private[this] def renderConfigField(storageStatus: StorageStatus) = {
    def renderConfigForm() = {
      val changed     = Var(false)
      val newConfigRx = Var(storageStatus.storageProps.data.utf8String)
      newConfigRx.triggerLater(changed() = true)

      Form(
        FormInput.textArea((), rows := 20, newConfigRx.reactiveInput, AppComponents.tabOverride),
        Form.submit(context.locale.submit, changed.reactiveShow),
        onsubmit := Callback.onSubmit { _ ⇒
          val newConfig = SerializedProps(storageStatus.storageProps.format, ByteString(newConfigRx.now))
          context.api
            .createStorage(storageId, newConfig)
            .foreach(_ ⇒ regionContext.updateStorage(storageId))
        }
      )
    }

    AppComponents.dropdown(context.locale.config)(renderConfigForm())
  }

  private[this] def renderRegionsRegistration(storageStatus: StorageStatus) = {
    def updateRegionList(newIdSet: Set[StorageId]) = {
      val currentIdSet = storageStatus.regions
      val toRegister   = newIdSet -- currentIdSet
      val toUnregister = currentIdSet -- newIdSet
      for {
        _ ← Future.sequence(toUnregister.map(context.api.unregisterStorage(_, storageId)))
        _ ← Future.sequence(toRegister.map(context.api.registerStorage(_, storageId)))
      } regionContext.updateAll()
    }

    def renderAddButton() = {
      def showAddDialog(): Unit = {
        val allIds                       = regionContext.regions.now.regions.keys.toSeq.sorted
        val (idSelect, idSelectRendered) = AppComponents.idSelect(context.locale.regions, allIds, storageStatus.regions.toSeq)
        Modal()
          .withTitle(context.locale.registerRegion)
          .withBody(Form(idSelectRendered))
          .withButtons(
            AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ updateRegionList(idSelect.selected.now.toSet))),
            AppComponents.modalClose()
          )
          .show()
      }

      Button(ButtonStyle.primary, ButtonSize.extraSmall)(
        AppIcons.register,
        context.locale.registerRegion,
        onclick := Callback.onClick(_ ⇒ showAddDialog())
      )
    }

    def renderRegion(storageId: StorageId) = {
      div(
        b(storageId),
        Bootstrap.textStyle.success
      )
    }

    val regionsSeq = storageStatus.regions.toSeq.sorted
    div(
      regionsSeq.map(renderRegion),
      renderAddButton()
    )
  }
}
