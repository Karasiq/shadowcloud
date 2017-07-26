package com.karasiq.shadowcloud.actors

import java.util.concurrent.TimeoutException

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, NotInfluenceReceiveTimeout, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.pattern.{ask, pipe}
import akka.util.{ByteString, Timeout}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.internal.GarbageCollectUtil
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.{ChunkPath, DeleteChunks ⇒ SDeleteChunks}
import com.karasiq.shadowcloud.actors.utils.{GCState, MessageStatus}
import com.karasiq.shadowcloud.actors.RegionIndex.WriteDiff
import com.karasiq.shadowcloud.config.StorageConfig
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.replication.StorageStatusProvider.StorageStatus
import com.karasiq.shadowcloud.storage.utils.{IndexMerger, StorageUtils}
import com.karasiq.shadowcloud.storage.utils.IndexMerger.RegionKey
import com.karasiq.shadowcloud.utils.{MemorySize, Utils}

object RegionGC {
  // Messages
  sealed trait Message
  case class CollectGarbage(delete: Option[Boolean] = None) extends Message with NotInfluenceReceiveTimeout
  object CollectGarbage extends MessageStatus[String, Map[String, GCState]]
  case class Defer(time: FiniteDuration) extends Message with NotInfluenceReceiveTimeout

  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class DeleteGarbage(states: Seq[(StorageStatus, GCState)]) extends InternalMessage

  // Props
  def props(regionId: String): Props = {
    Props(new RegionGC(regionId))
  }
}

private final class RegionGC(regionId: String) extends Actor with ActorLogging {
  import context.dispatcher

  import RegionGC._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] implicit val timeout: Timeout = Timeout(30 seconds)
  private[this] val sc = ShadowCloud()
  private[this] val config = sc.regionConfig(regionId)
  private[this] val gcSchedule = context.system.scheduler.schedule(5 minutes, 5 minutes, self, CollectGarbage())(dispatcher, self)
  private[this] var gcDeadline = Deadline.now

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receiveIdle: Receive = {
    case CollectGarbage(delete) ⇒
      if (sender() != self || gcDeadline.isOverdue()) {
        log.debug("Starting garbage collection")

        context.setReceiveTimeout(10 minutes)
        context.become(receiveCollecting(Set(sender()), delete.getOrElse(config.gcAutoDelete)))

        collectGarbage().map(DeleteGarbage).pipeTo(self)
      } else {
        log.debug("Garbage collection will be started in {} minutes", gcDeadline.timeLeft.toMinutes)
      }

    case Defer(time) ⇒
      if (gcDeadline.timeLeft < time) {
        log.debug("GC deferred to {}", time.toCoarsest)
        gcDeadline = time.fromNow
      }
  }

  private[this] def finishCollecting(receivers: Set[ActorRef], statesFuture: Future[Map[String, GCState]]): Unit = {
    CollectGarbage.wrapFuture(regionId, statesFuture).foreach { status ⇒
      receivers
        .filter(rv ⇒ rv != self && rv != Actor.noSender)
        .foreach(_ ! status)
    }

    context.setReceiveTimeout(Duration.Undefined)
    context.become(receiveIdle)
    self ! Defer(30 minutes)
  }

  def receiveCollecting(receivers: Set[ActorRef], delete: Boolean): Receive = {
    case CollectGarbage(delete1) ⇒
      val newReceiver = sender()
      val newDelete = delete1.fold(delete)(delete || _)
      context.become(receiveCollecting(receivers + newReceiver, newDelete))

    case DeleteGarbage(gcStates) ⇒
      val future = deleteGarbage(gcStates, delete)
        .filter(_.isSuccess)
        .map(_ ⇒ (for ((storage, state) ← gcStates) yield (storage.id, state)).toMap)
      finishCollecting(receivers, future)

    case Status.Failure(exc) ⇒
      finishCollecting(receivers, Future.failed(exc))

    case ReceiveTimeout ⇒
      val exception = new TimeoutException("GC timeout")
      finishCollecting(receivers, Future.failed(exception))
      throw exception
  }

  override def receive: Receive = receiveIdle

  override def postStop(): Unit = {
    gcSchedule.cancel()
    super.postStop()
  }

  // -----------------------------------------------------------------------
  // Orphaned chunks deletion
  // -----------------------------------------------------------------------
  private[this] def collectGarbage(): Future[Seq[(StorageStatus, GCState)]] = {
    def createGCState(index: IndexMerger[_], storage: StorageStatus): Future[(StorageStatus, GCState)] = {
      sc.ops.storage.getChunkKeys(storage.id).map { storageKeys ⇒
        val gcUtil = GarbageCollectUtil(storage.config)
        (storage, gcUtil.collect(index, storageKeys.filter(_.region == regionId).map(_.id)))
      }
    }

    for {
      regionIndex ← sc.ops.region.getIndex(regionId).map(IndexMerger.restore(RegionKey.zero, _))
      storages ← sc.ops.region.getStorages(regionId)
      states ← Future.sequence(storages.map(createGCState(regionIndex, _)))
    } yield states
  }

  private[this] def deleteGarbage(gcStates: Seq[(StorageStatus, GCState)], delete: Boolean): Future[StorageIOResult] = {
    def toDeleteFromStorage(config: StorageConfig, state: GCState): Set[ByteString] = {
      @inline def keys(chunks: Set[Chunk]): Set[ByteString] = chunks.map(config.chunkKey)

      keys(state.orphaned) ++ state.notIndexed
    }

    def toDeleteFromIndex(state: GCState): Set[Chunk] = {
      state.orphaned ++ state.notExisting
    }

    def deleteFromStorages(chunks: Seq[(StorageStatus, Set[ByteString])]): Future[StorageIOResult] = {
      val futures = chunks.map { case (storage, chunks) ⇒
        val paths = chunks.map(ChunkPath(regionId, _))
        if (log.isDebugEnabled) log.debug("Deleting chunks from storage {}: [{}]", storage.id, Utils.printHashes(chunks))
        SDeleteChunks.unwrapFuture(storage.dispatcher ? SDeleteChunks(paths))
      }
      StorageUtils.foldIOFutures(futures: _*)
    }

    def deleteFromIndexes(chunks: Seq[(StorageStatus, Set[Chunk])]): Future[Done] = {
      val futures = chunks.map { case (storage, chunks) ⇒
        if (log.isDebugEnabled) log.debug("Deleting chunks from index {}: [{}]", storage.id, Utils.printChunkHashes(chunks))
        WriteDiff.unwrapFuture(storage.dispatcher ?
          StorageIndex.Envelope(regionId, WriteDiff(IndexDiff.deleteChunks(chunks.toSeq: _*))))
      }
      Future.foldLeft(futures.toList)(Done)((_, _) ⇒ Done)
    }

    val toDelete = for {
      (storage, state) ← gcStates
      keys = toDeleteFromStorage(storage.config, state) if keys.nonEmpty
    } yield (storage, keys)

    val toUnIndex = for {
      (storage, state) ← gcStates
      chunks = toDeleteFromIndex(state) if chunks.nonEmpty
    } yield (storage, chunks)

    val hashes: Set[ByteString] = if (log.isWarningEnabled) {
      (toDelete.flatMap(_._2) ++ toUnIndex.flatMap(_._2).map(_.checksum.hash)).toSet
    } else {
      Set.empty
    }

    if (delete) {
      log.warning("Deleting orphaned chunks: [{}]", Utils.printHashes(hashes))

      val future = for {
        result ← deleteFromStorages(toDelete)
        _ ← deleteFromIndexes(toUnIndex)
      } yield result

      future.onComplete {
        case Success(result) ⇒
          if (log.isInfoEnabled) log.info("Orphaned chunks deleted: {}", MemorySize.toString(result.count))

        case Failure(error) ⇒
          log.error(error, "Error deleting orphaned chunks")
      }
      future
    } else {
      if (hashes.nonEmpty) log.warning("Orphaned chunks found: [{}]", Utils.printHashes(hashes, 50))
      Future.successful(StorageIOResult.Success("", 0))
    }
  }
}
