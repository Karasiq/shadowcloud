package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, NotInfluenceReceiveTimeout, Props}
import akka.pattern.pipe
import akka.util.Timeout
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent.StorageEnvelope
import com.karasiq.shadowcloud.actors.internal.{DiffStats, PendingOperation, StorageStatsTracker}
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}

import scala.concurrent.duration._
import scala.language.postfixOps

object StorageDispatcher {
  // Messages
  sealed trait Message
  object CheckHealth extends Message with NotInfluenceReceiveTimeout with MessageStatus[String, StorageHealth]

  // Props
  def props(storageId: String, index: ActorRef, chunkIO: ActorRef, health: StorageHealthProvider): Props = {
    Props(classOf[StorageDispatcher], storageId, index, chunkIO, health)
  }
}

class StorageDispatcher(storageId: String, index: ActorRef, chunkIO: ActorRef, health: StorageHealthProvider) extends Actor with ActorLogging {
  import StorageDispatcher._

  // Context
  import context.dispatcher
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] val schedule = context.system.scheduler.schedule(Duration.Zero, 30 seconds, self, CheckHealth)

  // State
  val pending = PendingOperation.chunk
  val stats = new StorageStatsTracker(storageId, health, log)

  def receive: Receive = {
    // -----------------------------------------------------------------------
    // Chunk commands
    // -----------------------------------------------------------------------
    case msg @ ChunkIODispatcher.WriteChunk(chunk) ⇒
      pending.addWaiter(chunk, sender(), { () ⇒
        log.debug("Writing chunk: {}", chunk)
        chunkIO ! msg
      })

    case msg @ ChunkIODispatcher.ReadChunk(chunk) ⇒
      log.debug("Reading chunk: {}", chunk)
      chunkIO.forward(msg)

    // -----------------------------------------------------------------------
    // Chunk responses
    // -----------------------------------------------------------------------
    case msg @ ChunkIODispatcher.WriteChunk.Success(_, chunk) ⇒
      log.debug("Chunk written, appending to index: {}", chunk)
      pending.finish(chunk, msg)
      StorageEvent.stream.publish(StorageEnvelope(storageId, StorageEvent.ChunkWritten(chunk)))
      index ! IndexSynchronizer.AddPending(IndexDiff.newChunks(chunk.withoutData))

    case msg @ ChunkIODispatcher.WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failure: {}", chunk)
      pending.finish(chunk, msg)

    // -----------------------------------------------------------------------
    // Index commands
    // -----------------------------------------------------------------------
    case msg: IndexSynchronizer.Message ⇒
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
    case StorageEnvelope(`storageId`, event) ⇒ event match {
      case StorageEvent.IndexLoaded(diffs) ⇒
        val newStats = diffs.foldLeft(DiffStats.empty)((stat, kv) ⇒ stat + DiffStats(kv._2))
        stats.updateStats(newStats)

      case StorageEvent.IndexUpdated(_, diff, _) ⇒
        stats.addStats(DiffStats(diff))

      case StorageEvent.ChunkWritten(chunk) ⇒
        val written = chunk.checksum.encryptedSize
        log.debug("{} bytes written, updating storage health", written)
        stats.updateHealth(_ - written)

      case _ ⇒
        // Ignore
    }
  }

  override def preStart(): Unit = {
    super.preStart()
    context.watch(chunkIO)
    context.watch(index)
    StorageEvent.stream.subscribe(self, storageId)
  }

  override def postStop(): Unit = {
    StorageEvent.stream.unsubscribe(self)
    schedule.cancel()
    super.postStop()
  }
}
