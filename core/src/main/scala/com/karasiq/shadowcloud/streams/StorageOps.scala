package com.karasiq.shadowcloud.streams

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.{GCState, MessageStatus}
import com.karasiq.shadowcloud.actors.GarbageCollector.CollectGarbage
import com.karasiq.shadowcloud.actors.IndexDispatcher.{CompactIndex, Synchronize}

object StorageOps {
  def apply(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout): StorageOps = {
    new StorageOps(regionSupervisor)
  }
}

final class StorageOps(regionSupervisor: ActorRef)(implicit ec: ExecutionContext, timeout: Timeout) {
  def synchronize(storageId: String): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, Synchronize)
  }

  def collectGarbage(storageId: String, startNow: Boolean = false, delete: Boolean = false): Future[Map[String, GCState]] = {
    doAsk(storageId, CollectGarbage, CollectGarbage(startNow, Some(delete)))
  }

  def compactIndex(storageId: String, region: String): Unit = {
    regionSupervisor ! StorageEnvelope(storageId, CompactIndex(region))
  }

  private[this] def doAsk[V](storageId: String, status: MessageStatus[_, V], message: Any): Future[V] = {
    (regionSupervisor ? StorageEnvelope(storageId, message)).flatMap {
      case status.Success(_, value) ⇒
        Future.successful(value)

      case status.Failure(_, error) ⇒
        Future.failed(error)
    }
  }
}
