package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, NotInfluenceReceiveTimeout, Props}
import akka.pattern.{ask, pipe}
import akka.util.{ByteString, Timeout}
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.internal.{DiffStats, GarbageCollector, PendingOperation, StorageStatsTracker}
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.utils.{IndexMerger, StorageUtils}
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider, StorageIOResult}
import com.karasiq.shadowcloud.utils.{MemorySize, Utils}

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object StorageDispatcher {
  // Messages
  sealed trait Message
  object CheckHealth extends Message with NotInfluenceReceiveTimeout with MessageStatus[String, StorageHealth]
  object CollectGarbage extends Message with NotInfluenceReceiveTimeout

  // Props
  def props(storageId: String, index: ActorRef, chunkIO: ActorRef, health: StorageHealthProvider): Props = {
    Props(classOf[StorageDispatcher], storageId, index, chunkIO, health)
  }
}

private final class StorageDispatcher(storageId: String, index: ActorRef, chunkIO: ActorRef, health: StorageHealthProvider) extends Actor with ActorLogging {
  import StorageDispatcher._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  import context.dispatcher
  private[this] val config = AppConfig().storage
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] val schedules = Array(
    context.system.scheduler.schedule(Duration.Zero, 30 seconds, self, CheckHealth),
    context.system.scheduler.schedule(10 minutes, 20 minutes, self, CollectGarbage)
  )

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  val writingChunks = PendingOperation.withRegionChunk
  val stats = StorageStatsTracker(storageId, health, log)
  var gcDeadline = Deadline.now

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    // -----------------------------------------------------------------------
    // Chunk commands
    // -----------------------------------------------------------------------
    case msg @ ChunkIODispatcher.WriteChunk(region, chunk) ⇒
      writingChunks.addWaiter((region, chunk), sender(), { () ⇒
        log.debug("Writing chunk: {}", chunk)
        chunkIO ! msg
      })

    case msg @ ChunkIODispatcher.ReadChunk(region, chunk) ⇒
      log.debug("Reading chunk: {}/{}", region, chunk)
      chunkIO.forward(msg)

    // -----------------------------------------------------------------------
    // Chunk responses
    // -----------------------------------------------------------------------
    case msg @ ChunkIODispatcher.WriteChunk.Success((region, chunk), _) ⇒
      log.debug("Chunk written, appending to index: {}", chunk)
      writingChunks.finish((region, chunk), msg)
      StorageEvents.stream.publish(StorageEnvelope(storageId, StorageEvents.ChunkWritten(region, chunk)))
      index ! IndexDispatcher.AddPending(region, IndexDiff.newChunks(chunk.withoutData))

    case msg @ ChunkIODispatcher.WriteChunk.Failure((region, chunk), error) ⇒
      log.error(error, "Chunk write failure: {}/{}", region, chunk)
      writingChunks.finish((region, chunk), msg)

    // -----------------------------------------------------------------------
    // Index commands
    // -----------------------------------------------------------------------
    case msg: IndexDispatcher.Message ⇒
      index.forward(msg)

    // -----------------------------------------------------------------------
    // Storage health
    // -----------------------------------------------------------------------
    case CheckHealth ⇒
      stats.checkHealth()
        .map(CheckHealth.Success(storageId, _))
        .recover(PartialFunction(CheckHealth.Failure(storageId, _)))
        .pipeTo(self)

    case CheckHealth.Success(`storageId`, health) ⇒
      stats.updateHealth(_ ⇒ health)

    case CheckHealth.Failure(`storageId`, error) ⇒
      log.error(error, "Health update failure: {}", storageId)

    // -----------------------------------------------------------------------
    // Storage maintenance
    // -----------------------------------------------------------------------
    case CollectGarbage ⇒
      if (gcDeadline.isOverdue() && writingChunks.count == 0) {
        log.debug("Starting garbage collection")
        deleteOrphanedChunks()
      } else {
        log.debug("Garbage collection will be started in {} minutes", gcDeadline.timeLeft.toMinutes)
      }

    // -----------------------------------------------------------------------
    // Storage events
    // -----------------------------------------------------------------------
    case StorageEnvelope(`storageId`, event: StorageEvents.Event) ⇒ event match {
      case StorageEvents.IndexLoaded(diffMap) ⇒
        val allDiffs = diffMap.values.flatMap(_.values).toSeq
        val newStats = DiffStats(allDiffs:_*)
        stats.updateStats(newStats)

      case StorageEvents.IndexUpdated(_, _, diff, _) ⇒
        stats.appendStats(DiffStats(diff))

      case StorageEvents.ChunkWritten(_, chunk) ⇒
        val written = chunk.checksum.encryptedSize
        log.debug("{} bytes written, updating storage health", written)
        stats.updateHealth(_ - written)
        gcDeadline = 1 hour fromNow

      case _ ⇒
        // Ignore
    }
  }

  // -----------------------------------------------------------------------
  // Garbage collection
  // -----------------------------------------------------------------------
  private[this] def collectOrphanedChunks(): Future[Map[String, Set[ByteString]]] = {
    val indexes = IndexDispatcher.GetIndexes.unwrapFuture(index ? IndexDispatcher.GetIndexes)
      .map(_.mapValues(IndexMerger.restore(0L, _)))
    val keys = ChunkIODispatcher.GetKeys.unwrapFuture(chunkIO ? ChunkIODispatcher.GetKeys)
      .map(_.groupBy(_._1).mapValues(_.map(_._2)))

    indexes.zip(keys).map { case (indexes, chunks) ⇒
      indexes.map { case (region, index) ⇒
        val gc = GarbageCollector(config, index)
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
              if (log.isInfoEnabled) log.info("Orphaned chunks deleted: {}", MemorySize.toString(result.count))

            case Failure(error) ⇒
              log.error(error, "Error deleting orphaned chunks")
          }
        }

      case Failure(error) ⇒
        log.error(error, "Storage {} garbage collection failed", storageId)
    }
  }

  // -----------------------------------------------------------------------
  // Lifecycle hooks
  // -----------------------------------------------------------------------
  override def preStart(): Unit = {
    super.preStart()
    context.watch(chunkIO)
    context.watch(index)
    StorageEvents.stream.subscribe(self, storageId)
  }

  override def postStop(): Unit = {
    StorageEvents.stream.unsubscribe(self)
    schedules.foreach(_.cancel())
    super.postStop()
  }
}
