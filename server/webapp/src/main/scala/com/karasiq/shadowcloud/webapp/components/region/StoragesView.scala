package com.karasiq.shadowcloud.webapp.components.region

import akka.util.ByteString
import rx.{Rx, Var}
import rx.async._

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext

object StoragesView {
  def apply()(implicit context: AppContext, regionContext: RegionContext): StoragesView = {
    new StoragesView
  }

  private def newStorageName()(implicit regionContext: RegionContext): StorageId = {
    s"storage-${regionContext.regions.now.storages.size}"
  }
}

class StoragesView(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val storageViewsRx = regionContext.regions.fold(Map.empty[RegionId, Tag]) { case (views, report) ⇒
      val newMap = report.storages.map { case (storageId, _) ⇒
        storageId → views.getOrElse(storageId, renderStorage(storageId))
      }
      newMap
    }

    div(
      renderAddButton(),
      Rx(div(storageViewsRx().toSeq.sortBy(_._1).map(_._2)))
    )
  }

  private[this] def renderAddButton() = {
    def doCreate(storageId: StorageId, propsString: String) = {
      val props = SerializedProps(SerializedProps.DefaultFormat, ByteString(propsString))
      context.api.createStorage(storageId, props).foreach { _ ⇒
        regionContext.updateAll()
      }
    }

    def showCreateDialog() = {
      val newStorageNameRx = Var(StoragesView.newStorageName())
      val propsRx = Var("")

      val storageTypeSelect = {
        val storageTypesRx = context.api.getStorageTypes().toRx(Set.empty).map(_.toSeq.sorted)
        FormInput.select(context.locale.storageType, Rx(storageTypesRx().map(st ⇒ FormSelectOption(st, st))))
      }

      storageTypeSelect.selected.map(_.headOption).foreach {
        case Some(storageType) ⇒
          context.api.getDefaultStorageConfig(storageType)
            .map(_.data.utf8String)
            .foreach(propsRx.update)

        case None ⇒
          // Ignore
      }

      Modal()
        .withTitle(context.locale.createStorage)
        .withBody(Form(
          FormInput.text(context.locale.regionId, newStorageNameRx.reactiveInput),
          storageTypeSelect,
          FormInput.textArea(context.locale.config, rows := 20, Rx(placeholder := propsRx()).auto,
            AppComponents.tabOverride, propsRx.reactiveInput)
        ))
        .withButtons(
          AppComponents.modalSubmit(onclick := Callback.onClick(_ ⇒ doCreate(newStorageNameRx.now, propsRx.now))),
          AppComponents.modalClose()
        )
        .show()
    }

    Button(ButtonStyle.primary, ButtonSize.small, block = true)(
      AppIcons.create, context.locale.createStorage,
      onclick := Callback.onClick(_ ⇒ showCreateDialog())
    )
  }

  private[this] def renderStorage(storageId: StorageId) = {
    lazy val storageConfigView = StorageConfigView(storageId)
    AppComponents.dropdown(storageId) {
      Bootstrap.well(storageConfigView)
    }
  }
}

