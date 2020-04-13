package com.karasiq.shadowcloud.webapp.components.region

import akka.util.ByteString
import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.config.SerializedProps
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.webapp.components.common.{AppComponents, AppIcons}
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import rx.{Rx, Var}
import scalaTags.all._

object RegionsView {
  def apply()(implicit context: AppContext, regionContext: RegionContext): RegionsView = {
    new RegionsView
  }

  private def newRegionId()(implicit rc: RegionContext): RegionId = {
    s"region-${rc.regions.now.regions.size}"
  }

  private def uniqueRegionId(id: RegionId): RegionId = {
    def timestampString = "-u" + System.currentTimeMillis().toHexString

    val regex = "-u\\w+$".r
    val prefix = regex.findFirstMatchIn(id) match {
      case Some(rm) ⇒ rm.before
      case None     ⇒ id
    }
    prefix + timestampString
  }
}

class RegionsView(implicit context: AppContext, regionContext: RegionContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val regionViewsRx = regionContext.regions.fold(Map.empty[RegionId, Tag]) {
      case (views, report) ⇒
        val newMap = report.regions.map {
          case (regionId, _) ⇒
            regionId → views.getOrElse(regionId, renderRegion(regionId))
        }
        newMap
    }

    div(
      renderAddButton(),
      Rx(div(regionViewsRx().toSeq.sortBy(_._1).map(_._2)))
    )
  }

  private[this] def renderAddButton() = {
    def doCreate(regionId: RegionId) = {
      val defaultConfig = {
        // Security patch
        val cfg = """
                    |chunk-key = com.karasiq.shadowcloud.storage.utils.mappers.HashNonceHMACKeyMapper
                    |""".stripMargin

        SerializedProps(SerializedProps.DefaultFormat, ByteString(cfg))
      }

      context.api.createRegion(regionId, defaultConfig).foreach { _ ⇒
        regionContext.updateAll()
      }
    }

    def showCreateDialog() = {
      val newRegionIdRx = Var(RegionsView.newRegionId())
      Modal()
        .withTitle(context.locale.createRegion)
        .withBody(
          Form(
            FormInput.text(context.locale.regionId, newRegionIdRx.reactiveInput)(div(small(context.locale.regionIdHint)))
          )
        )
        .withButtons(
          AppComponents.modalSubmit(onclick := Callback.onClick { _ ⇒
            // Utils.toSafeIdentifier(newRegionNameRx.now)
            doCreate(newRegionIdRx.now)
          }),
          Button(ButtonStyle.info)(context.locale.uniqueRegionId, onclick := Callback.onClick { _ ⇒
            newRegionIdRx() = RegionsView.uniqueRegionId(newRegionIdRx.now)
          }),
          AppComponents.modalClose()
        )
        .show()
    }

    Button(ButtonStyle.primary, ButtonSize.small, block = true)(
      AppIcons.create,
      context.locale.createRegion,
      onclick := Callback.onClick(_ ⇒ showCreateDialog())
    )
  }

  private[this] def renderRegion(regionId: RegionId) = {
    lazy val regionConfigView = RegionConfigView(regionId)
    AppComponents.dropdown(regionId) {
      Bootstrap.well(regionConfigView)
    }
  }
}
