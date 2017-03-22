package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, PossiblyHarmful, Props}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.karasiq.shadowcloud.actors.internal.GarbageCollectUtil
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.utils.{IndexMerger, StorageUtils}
import com.karasiq.shadowcloud.utils.{MemorySize, Utils}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

private[actors] object GarbageCollector {
  // Messages
  sealed trait Message
  case object CollectGarbage extends Message
  case class Defer(time: FiniteDuration) extends Message

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful

  // Events
  sealed trait Event

  // Props
  def props(index: ActorRef, chunkIO: ActorRef): Props = {
    Props(classOf[GarbageCollector], index, chunkIO)
  }
}

private[actors] final class GarbageCollector(index: ActorRef, chunkIO: ActorRef) extends Actor with ActorLogging {
  import GarbageCollector._
  import context.dispatcher

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val timeout = Timeout(30 seconds)
  private[this] val config = AppConfig().storage
  private[this] val gcSchedule = context.system.scheduler.schedule(5 minutes, 5 minutes, self, CollectGarbage)
  private[this] var gcDeadline = Deadline.now

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  override def receive: Receive = {
    case CollectGarbage ⇒
      if (gcDeadline.isOverdue()) {
        log.debug("Starting garbage collection")
        deleteOrphanedChunks()
      } else {
        log.debug("Garbage collection will be started in {} minutes", gcDeadline.timeLeft.toMinutes)
      }

    case Defer(time) ⇒
      if (gcDeadline.timeLeft < time) {
        log.debug("GC deferred to {}", time.toCoarsest)
        gcDeadline = time.fromNow
      }
  }

  override def postStop(): Unit = {
    gcSchedule.cancel()
    super.postStop()
  }

  // -----------------------------------------------------------------------
  // Orphaned chunks deletion
  // -----------------------------------------------------------------------
  private[this] def collectOrphanedChunks(): Future[Map[String, Set[ByteString]]] = {
    val indexes = IndexDispatcher.GetIndexes.unwrapFuture(index ? IndexDispatcher.GetIndexes)
      .map(_.mapValues(IndexMerger.restore(0L, _)))
    val keys = ChunkIODispatcher.GetKeys.unwrapFuture(chunkIO ? ChunkIODispatcher.GetKeys)
      .map(_.groupBy(_._1).mapValues(_.map(_._2)))

    indexes.zip(keys).map { case (indexes, chunks) ⇒
      indexes.map { case (region, index) ⇒
        val gc = GarbageCollectUtil(config, index)
        val actual = chunks.getOrElse(region, Set.empty)
        val orphaned = gc.orphanedChunks.map(config.chunkKey)
        val unknown = gc.notIndexedChunks(actual)
        region → (orphaned ++ unknown)
      }.filter(_._2.nonEmpty)
    }
  }

  private[this] def deleteChunks(chunks: Map[String, Set[ByteString]]): Future[StorageIOResult] = {
    val futures = chunks.map { case (region, chunks) ⇒
      ChunkIODispatcher.DeleteChunks
        .unwrapFuture(chunkIO ? ChunkIODispatcher.DeleteChunks(region, chunks))
    }
    StorageUtils.foldIOFutures(futures.toSeq: _*)
  }

  private[this] def deleteOrphanedChunks(): Unit = {
    collectOrphanedChunks().onComplete {
      case Success(orphaned) ⇒
        if (orphaned.nonEmpty) {
          if (log.isWarningEnabled) {
            log.warning("Deleting orphaned chunks: {}", orphaned.mapValues(hs ⇒ s"[${Utils.printHashes(hs)}]"))
          }
          deleteChunks(orphaned).onComplete {
            case Success(result) ⇒
              self ! Defer(30 minutes)
              if (log.isInfoEnabled) log.info("Orphaned chunks deleted: {}", MemorySize.toString(result.count))

            case Failure(error) ⇒
              log.error(error, "Error deleting orphaned chunks")
          }
        } else {
          self ! Defer(1 hour)
        }

      case Failure(error) ⇒
        log.error(error, "Garbage collection failed")
    }
  }
}
