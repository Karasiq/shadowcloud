package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.exceptions.StorageException
import com.karasiq.shadowcloud.model.utils.SyncReport
import com.karasiq.shadowcloud.model.{RegionId, StorageId}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.CategorizedRepository
import com.karasiq.shadowcloud.storage.utils.{IndexMerger, StorageUtils}
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.mutable.{AnyRefMap => MMap}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object StorageIndex {
  // Messages
  sealed trait Message
  case class OpenIndex(regionId: RegionId)                              extends Message
  case class CloseIndex(regionId: RegionId, clear: Boolean = false)     extends Message
  case object GetIndexes                                                extends Message with MessageStatus[StorageId, Map[String, IndexMerger.State[Long]]]
  case object SynchronizeAll                                            extends Message with MessageStatus[StorageId, Map[RegionId, SyncReport]]
  case class Envelope(regionId: RegionId, message: RegionIndex.Message) extends Message

  // Props
  def props(storageId: StorageId, storageProps: StorageProps, repository: CategorizedRepository[String, Long]): Props = {
    Props(new StorageIndex(storageId, storageProps, repository))
  }
}

private[actors] final class StorageIndex(storageId: StorageId, storageProps: StorageProps, repository: CategorizedRepository[String, Long])
    extends Actor
    with ActorLogging {

  import StorageIndex._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val executionContext: ExecutionContext = context.dispatcher
  private[this] implicit val timeout: Timeout                   = Timeout(500 millis)
  private[this] val subIndexes                                  = MMap.empty[String, ActorRef]

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    case Envelope(regionId, message) ⇒
      subIndexes.get(regionId) match {
        case Some(dispatcher) ⇒
          dispatcher.forward(message)

        case None ⇒
          sender() ! Status.Failure(StorageException.NotFound(StorageUtils.toStoragePath(regionId)))
      }

    case SynchronizeAll ⇒
      log.debug("Synchronizing all indexes")
      val futures = subIndexes.map {
        case (regionId, dispatcher) ⇒
          RegionIndex.Synchronize.unwrapFuture(dispatcher ? RegionIndex.Synchronize).map((regionId, _))
      }
      val result = Future.sequence(futures.toVector).map(_.toMap)
      SynchronizeAll.wrapFuture(storageId, result).pipeTo(sender())

    case OpenIndex(regionId) ⇒
      log.debug("Starting region index dispatcher: {}", regionId)
      startRegionDispatcher(regionId)

    case CloseIndex(regionId, clear) ⇒
      log.debug("Stopping region index dispatcher: {}", regionId)
      stopRegionDispatcher(regionId, clear)

    case GetIndexes ⇒
      val futures = subIndexes.map {
        case (regionId, dispatcher) ⇒
          val future = RegionIndex.GetIndex.unwrapFuture(dispatcher ? RegionIndex.GetIndex)
          future.map((regionId, _))
      }
      Future
        .sequence(futures)
        .map(values ⇒ GetIndexes.Success(storageId, values.toMap))
        .recover { case exc ⇒ GetIndexes.Failure(storageId, exc) }
        .pipeTo(sender())
  }

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] def startRegionDispatcher(regionId: RegionId): Unit = {
    if (!subIndexes.contains(regionId)) {
      val newDispatcher =
        context.actorOf(RegionIndex.props(storageId, regionId, storageProps, repository.subRepository(regionId)), Utils.uniqueActorName(regionId))
      subIndexes += (regionId, newDispatcher)
    }
  }

  private[this] def stopRegionDispatcher(regionId: RegionId, clear: Boolean): Unit = {
    subIndexes.remove(regionId).foreach { actor =>
      if (clear)
        (actor ? RegionIndex.DeleteHistory)
          .map(_ => Done)
          .recover { case _ => Done }
          .foreach(_ => context.stop(actor))
      else context.stop(actor)
    }
  }
}
