package com.karasiq.shadowcloud.webapp.components.region

import akka.NotUsed
import akka.util.ByteString
import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.api.json.SCJsonEncoding
import com.karasiq.shadowcloud.model.keys.KeyChain
import com.karasiq.shadowcloud.model.utils.RegionStateReport
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.components.keys.KeysContext
import com.karasiq.shadowcloud.webapp.context.AppContext
import play.api.libs.json.Json
import rx.Var
import scalaTags.all._

import scala.concurrent.Future
import scala.reflect.api
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ExportImportModal {
  import SCJsonEncoding.implicits._

  final case class ExportEntity(state: RegionStateReport, keys: KeyChain)
  object ExportEntity {
    implicit val jsonFormat = Json.format[ExportEntity]
  }

  def exportDialog()(implicit context: AppContext): Unit = {
    val future = for {
    regions <- context.api.getRegions()
    keys <- context.api.getKeys()
    } yield ExportEntity(regions, keys)

    future.foreach { snapshot =>
      val json = Json.prettyPrint(Json.toJson(snapshot))
      Modal()
        .withTitle("Export")
        .withBody(FormInput.textArea("JSON", rows := 30, json))
        .withButtons(Modal.closeButton(context.locale.close))
        .show()
    }
  }

  def importDialog()(implicit context: AppContext, rc: RegionContext, kc: KeysContext): Unit = {
    val input = Var("")
    Modal()
      .withTitle("Export")
      .withBody(FormInput.textArea("JSON", rows := 30, input.reactiveInputRead))
      .withButtons(AppComponents.modalSubmit(onclick := Callback.onClick { _  =>
        val snapshot = Json.parse(input.now).as[ExportEntity]
        val regions = snapshot.state

        def createKeys() = Future.sequence(snapshot.keys.keys.map { kp =>
          context.api.addKey(kp.key, kp.regionSet, kp.forEncryption, kp.forDecryption).recover { case _ => null }.map(_ => NotUsed)
        })

        def createStorages() = Future.sequence(regions.storages.map { case (id, storage) =>
          for {
            st <- context.api.createStorage(id, storage.storageProps)
            _ <- if (storage.suspended) context.api.suspendStorage(id) else Future.successful(())
          } yield st
        })

        def createRegions() = Future.sequence(regions.regions.map { case (id, region) =>
          for {
            st <- context.api.createRegion(id, region.regionConfig)
            _ <- context.api.suspendRegion(id) // Always suspend
            _ <- Future.sequence(region.storages.map(storageId => context.api.registerStorage(id, storageId)))
          } yield st
        })

        for {
          _ <- createKeys()
          _ <- createStorages()
          _ <- createRegions()
        } {
          rc.updateAll()
          kc.updateAll()
        }
      }), Modal.closeButton(context.locale.close))
      .show()
  }
}
