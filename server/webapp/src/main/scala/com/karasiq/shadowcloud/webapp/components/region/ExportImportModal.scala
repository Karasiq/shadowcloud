package com.karasiq.shadowcloud.webapp.components.region

import akka.NotUsed
import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.model.keys.KeyChain
import com.karasiq.shadowcloud.model.utils.RegionStateReport
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.components.keys.KeysContext
import com.karasiq.shadowcloud.webapp.context.AppContext
import play.api.libs.json.Json

import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object ExportImportModal {
  import com.karasiq.shadowcloud.api.json.SCJsonEncoding.implicits._

  final case class ExportEntity(state: RegionStateReport, keys: KeyChain)
  object ExportEntity {
    implicit val jsonFormat = Json.format[ExportEntity]
  }

  def exportDialog()(implicit context: AppContext): Unit = {
    val future = for {
      regions <- context.api.getRegions()
      keys    <- context.api.getKeys()
    } yield ExportEntity(regions, keys)

    future.foreach { snapshot =>
      val json = Json.prettyPrint(Json.toJson(snapshot))
      AppComponents.exportDialog(context.locale.export, "sc-export.json", json).show()
    }
  }

  def importDialog()(implicit context: AppContext, rc: RegionContext, kc: KeysContext): Unit = {
    AppComponents
      .importDialog(context.locale.importKey) { result =>
        val snapshot = Json.parse(result).as[ExportEntity]
        val regions  = snapshot.state

        def createKeys() =
          Future.sequence(snapshot.keys.keys.map { kp =>
            context.api.addKey(kp.key, kp.regionSet, kp.forEncryption, kp.forDecryption).recover { case _ => null }.map(_ => NotUsed)
          })

        def createStorages() =
          Future.sequence(regions.storages.map {
            case (id, storage) =>
              for {
                st <- context.api.createStorage(id, storage.storageProps)
                _  <- if (storage.suspended) context.api.suspendStorage(id) else Future.successful(())
              } yield st
          })

        def createRegions() =
          Future.sequence(regions.regions.map {
            case (id, region) =>
              for {
                st <- context.api.createRegion(id, region.regionConfig)
                _  <- context.api.suspendRegion(id) // Always suspend
                _  <- Future.sequence(region.storages.map(storageId => context.api.registerStorage(id, storageId)))
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
      }
      .show()
  }
}
