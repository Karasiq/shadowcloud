package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, NotInfluenceReceiveTimeout, Props}
import akka.pattern.pipe
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.internal.{DiffStats, PendingOperation, StorageStatsTracker}
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.concurrent.duration._
import scala.language.postfixOps

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
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] val schedule = context.system.scheduler.schedule(Duration.Zero, 30 seconds, self, CheckHealth)
  private[this] val gcActor = context.actorOf(GarbageCollector.props(index, chunkIO), "garbageCollector")

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] val writingChunks = PendingOperation.withRegionChunk
  private[this] val stats = StorageStatsTracker(storageId, health, log)

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

    case msg: ChunkIODispatcher.Message ⇒
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
    // Storage events
    // -----------------------------------------------------------------------
    case StorageEnvelope(`storageId`, event: StorageEvents.Event) ⇒ event match {
      case StorageEvents.IndexLoaded(diffMap) ⇒
        diffMap.foreach { case (region, diffs) ⇒
          val newStats = DiffStats(diffs.values.toSeq:_*)
          stats.updateStats(region, newStats)
        }
        gcActor ! GarbageCollector.Defer(10 minutes)

      case StorageEvents.IndexUpdated(region, _, diff, _) ⇒
        stats.appendStats(region, DiffStats(diff))
        val forCompact = stats.requiresCompaction()
        if (forCompact.nonEmpty) {
          log.debug("Requesting compaction of indexes: {}", forCompact)
          forCompact.foreach { region ⇒
            stats.clear(region)
            index ! IndexDispatcher.CompactIndex(region)
          }
        }
        gcActor ! GarbageCollector.Defer(10 minutes)

      case StorageEvents.IndexDeleted(region, _) ⇒
        stats.clear(region)

      case StorageEvents.ChunkWritten(_, chunk) ⇒
        val written = chunk.checksum.encryptedSize
        log.debug("{} bytes written, updating storage health", written)
        stats.updateHealth(_ - written)
        gcActor ! GarbageCollector.Defer(30 minutes)

      case _ ⇒
        // Ignore
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
    schedule.cancel()
    super.postStop()
  }
}
