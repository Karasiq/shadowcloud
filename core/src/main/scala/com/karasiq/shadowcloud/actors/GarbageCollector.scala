package com.karasiq.shadowcloud.actors

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.{higherKinds, postfixOps}
import scala.util.{Failure, Success}

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.internal.GarbageCollectUtil
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.{ChunkIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.utils.{IndexMerger, StorageUtils}
import com.karasiq.shadowcloud.utils.{MemorySize, Utils}

object GarbageCollector {
  // Messages
  sealed trait Message
  case class CollectGarbage(force: Boolean = false) extends Message
  case class Defer(time: FiniteDuration) extends Message

  // Props
  def props(storageId: String, index: ActorRef, chunkIO: ActorRef): Props = {
    Props(classOf[GarbageCollector], storageId, index, chunkIO)
  }
}

private final class GarbageCollector(storageId: String, index: ActorRef, chunkIO: ActorRef) extends Actor with ActorLogging {
  import context.dispatcher

  import GarbageCollector._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val timeout = Timeout(30 seconds)
  private[this] val sc = ShadowCloud()
  private[this] val config = sc.storageConfig(storageId)
  private[this] val gcSchedule = context.system.scheduler.schedule(5 minutes, 5 minutes, self, CollectGarbage())
  private[this] var gcDeadline = Deadline.now

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  override def receive: Receive = {
    case CollectGarbage(force) ⇒
      if (force || gcDeadline.isOverdue()) {
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
  private[this] def collectOrphanedChunks(): Future[Map[String, GarbageCollectUtil.State]] = {
    val indexes = IndexDispatcher.GetIndexes.unwrapFuture(index ? IndexDispatcher.GetIndexes)
      .map(_.mapValues(IndexMerger.restore(0L, _)))
    val keys = ChunkIODispatcher.GetKeys.unwrapFuture(chunkIO ? ChunkIODispatcher.GetKeys)
      .map(_.groupBy(_.region).mapValues(_.map(_.id)))

    indexes.zip(keys).map { case (indexes, chunkKeys) ⇒
      val gcUtil = GarbageCollectUtil(config)
      indexes.map { case (region, index) ⇒
        val storageChunks = chunkKeys.getOrElse(region, Set.empty)
        region → gcUtil.collect(index, storageChunks)
      }.filter(_._2.nonEmpty)
    }
  }

  private[this] def deleteOrphanedChunks(): Unit = {
    def toDeleteFromStorage(state: GarbageCollectUtil.State): Set[ByteString] = {
      @inline def keys(chunks: Set[Chunk]): Set[ByteString] = chunks.map(config.chunkKey)
      keys(state.orphaned) ++ state.notIndexed
    }

    def toDeleteFromIndex(state: GarbageCollectUtil.State): Set[Chunk] = {
      state.orphaned ++ state.notExisting
    }

    def deleteChunksFromStorage(chunks: Map[String, Set[ByteString]]): Future[StorageIOResult] = {
      val futures = chunks.map { case (region, chunks) ⇒
        val paths = chunks.map(ChunkPath(region, _))
        if (log.isDebugEnabled) log.debug("Deleting chunks from storage {}: {}", region, Utils.printHashes(chunks))
        ChunkIODispatcher.DeleteChunks.unwrapFuture(chunkIO ? ChunkIODispatcher.DeleteChunks(paths))
      }
      StorageUtils.foldIOFutures(futures.toSeq: _*)
    }

    def deleteChunksFromIndex(chunks: Map[String, Set[Chunk]]): Unit = {
      chunks.foreach { case (region, chunks) ⇒
        if (log.isDebugEnabled) log.debug("Deleting chunks from index {}: {}", region, Utils.printChunkHashes(chunks))
        index ! IndexDispatcher.AddPending(region, IndexDiff(chunks = ChunkIndexDiff(deletedChunks = chunks)))
      }
    }

    collectOrphanedChunks().onComplete {
      case Success(gcStates) ⇒
        if (gcStates.nonEmpty) {
          log.warning("Deleting orphaned chunks: {}", gcStates)
          deleteChunksFromStorage(gcStates.mapValues(toDeleteFromStorage)).onComplete {
            case Success(result) ⇒
              deleteChunksFromIndex(gcStates.mapValues(toDeleteFromIndex))
              self ! Defer(20 minutes)
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
