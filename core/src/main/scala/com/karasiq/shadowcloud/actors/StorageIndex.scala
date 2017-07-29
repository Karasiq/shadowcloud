package com.karasiq.shadowcloud.actors

import scala.collection.mutable.{AnyRefMap ⇒ MMap}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorLogging, ActorRef, Props, Status}
import akka.pattern.{ask, pipe}
import akka.util.Timeout

import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.CategorizedRepository
import com.karasiq.shadowcloud.storage.utils.IndexMerger

object StorageIndex {
  private type LocalKey = (String, Long)

  // Messages
  sealed trait Message
  case class OpenIndex(regionId: String) extends Message
  case class CloseIndex(regionId: String) extends Message
  case object GetIndexes extends Message with MessageStatus[String, Map[String, IndexMerger.State[Long]]]
  case object SynchronizeAll extends Message
  case class Envelope(regionId: String, message: RegionIndex.Message) extends Message

  // Props
  def props(storageId: String, storageProps: StorageProps, repository: CategorizedRepository[String, Long]): Props = {
    Props(new StorageIndex(storageId, storageProps, repository))
  }
}

private final class StorageIndex(storageId: String, storageProps: StorageProps, repository: CategorizedRepository[String, Long])
  extends Actor with ActorLogging {

  import StorageIndex._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val executionContext: ExecutionContext = context.dispatcher
  private[this] implicit val timeout: Timeout = Timeout(500 millis)
  private[this] val subIndexes = MMap.empty[String, ActorRef]

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    case Envelope(regionId, message) ⇒
      subIndexes.get(regionId) match {
        case Some(dispatcher) ⇒
          dispatcher.forward(message)

        case None ⇒
          sender() ! Status.Failure(new NoSuchElementException(regionId))
      }

    case SynchronizeAll ⇒
      log.debug("Synchronizing all indexes")
      subIndexes.values.foreach(_.forward(RegionIndex.Synchronize))

    case OpenIndex(regionId) ⇒
      log.debug("Starting region index dispatcher: {}", regionId)
      startRegionDispatcher(regionId)

    case CloseIndex(regionId) ⇒
      log.debug("Stopping region index dispatcher: {}", regionId)
      stopRegionDispatcher(regionId)

    case GetIndexes ⇒
      val futures = subIndexes.map { case (regionId, dispatcher) ⇒
        val future = RegionIndex.GetIndex.unwrapFuture(dispatcher ? RegionIndex.GetIndex)
        future.map((regionId, _))
      }
      Future.sequence(futures)
        .map(values ⇒ GetIndexes.Success(storageId, values.toMap))
        .recover { case exc ⇒ GetIndexes.Failure(storageId, exc) }
        .pipeTo(sender())
  }

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] def startRegionDispatcher(regionId: String): Unit = {
    if (subIndexes.contains(regionId)) return
    val newDispatcher = context.actorOf(RegionIndex.props(storageId, regionId, storageProps, repository.subRepository(regionId)))
    subIndexes += (regionId, newDispatcher)
  }

  private[this] def stopRegionDispatcher(regionId: String): Unit = {
    subIndexes.get(regionId).foreach(context.stop)
  }
}
