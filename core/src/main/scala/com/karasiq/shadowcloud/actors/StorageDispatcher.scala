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

  // Props
  def props(storageId: String, index: ActorRef, chunkIO: ActorRef, health: StorageHealthProvider): Props = {
    Props(classOf[StorageDispatcher], storageId, index, chunkIO, health)
  }
}

class StorageDispatcher(storageId: String, index: ActorRef, chunkIO: ActorRef, health: StorageHealthProvider) extends Actor with ActorLogging {
  import StorageDispatcher._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  import context.dispatcher
  private[this] implicit val timeout = Timeout(10 seconds)
  private[this] val schedule = context.system.scheduler.schedule(Duration.Zero, 30 seconds, self, CheckHealth)

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  val pending = PendingOperation.withChunk
  val stats = StorageStatsTracker(storageId, health, log)

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
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
      StorageEvents.stream.publish(StorageEnvelope(storageId, StorageEvents.ChunkWritten(chunk)))
      index ! IndexDispatcher.AddPending(IndexDiff.newChunks(chunk.withoutData))

    case msg @ ChunkIODispatcher.WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failure: {}", chunk)
      pending.finish(chunk, msg)

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
      case StorageEvents.IndexLoaded(diffs) ⇒
        val newStats = DiffStats(diffs.map(_._2):_*)
        stats.updateStats(newStats)

      case StorageEvents.IndexUpdated(_, diff, _) ⇒
        stats.appendStats(DiffStats(diff))

      case StorageEvents.ChunkWritten(chunk) ⇒
        val written = chunk.checksum.encryptedSize
        log.debug("{} bytes written, updating storage health", written)
        stats.updateHealth(_ - written)

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
