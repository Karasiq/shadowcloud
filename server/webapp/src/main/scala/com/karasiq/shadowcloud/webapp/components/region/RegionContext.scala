package com.karasiq.shadowcloud.webapp.components.region

import rx.{Ctx, Rx, Var}

import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.model.utils.RegionStateReport
import com.karasiq.shadowcloud.model.utils.RegionStateReport.{RegionStatus, StorageStatus}
import com.karasiq.shadowcloud.webapp.context.AppContext
import AppContext.JsExecutionContext

trait RegionContext {
  def regions: Rx[RegionStateReport]
  def region(id: RegionId): Rx[RegionStatus]
  def storage(id: StorageId): Rx[StorageStatus]

  def updateAll(): Unit
  def updateRegion(id: RegionId): Unit
  def updateStorage(id: StorageId): Unit
}

object RegionContext {
  def apply()(implicit ac: AppContext, ctx: Ctx.Owner): RegionContext = {
    val context = new RegionContext {
      private[this] val _stateReport = Var(RegionStateReport.empty)

      def regions: Rx[RegionStateReport] = _stateReport
      def region(id: RegionId): Rx[RegionStatus] = regions.map(_.regions(id))
      def storage(id: StorageId): Rx[StorageStatus] = regions.map(_.storages(id))

      def updateAll(): Unit = {
        ac.api.getRegions().foreach(_stateReport.update)
      }

      def updateRegion(id: RegionId): Unit = {
        ac.api.getRegion(id).foreach { newStatus ⇒
          val currentStatus = _stateReport.now
          _stateReport() = _stateReport.now.copy(regions = currentStatus.regions + (id → newStatus))
        }
      }

      def updateStorage(id: StorageId): Unit = {
        ac.api.getStorage(id).foreach { newStatus ⇒
          _stateReport() = _stateReport.now.copy(storages = _stateReport.now.storages + (id → newStatus))
        }
      }
    }

    context.updateAll()
    context
  }
}
