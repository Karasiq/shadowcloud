package com.karasiq.shadowcloud.actors

import scala.concurrent.duration._
import scala.language.postfixOps

import akka.actor.{Actor, ActorLogging, ActorRef, NotInfluenceReceiveTimeout, Props}
import akka.pattern.pipe
import akka.util.Timeout

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperation}
import com.karasiq.shadowcloud.actors.RegionIndex.WriteDiff
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}
import com.karasiq.shadowcloud.storage.props.StorageProps

object StorageDispatcher {
  // Messages
  sealed trait Message
  object CheckHealth extends Message with NotInfluenceReceiveTimeout with MessageStatus[String, StorageHealth]

  // Props
  def props(storageId: String, storageProps: StorageProps, index: ActorRef, chunkIO: ActorRef, health: StorageHealthProvider): Props = {
    Props(new StorageDispatcher(storageId, storageProps, index, chunkIO, health))
  }
}

private final class StorageDispatcher(storageId: String, storageProps: StorageProps, index: ActorRef,
                                      chunkIO: ActorRef, healthProvider: StorageHealthProvider) extends Actor with ActorLogging {
  import StorageDispatcher._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  import context.dispatcher

  private[this] implicit val timeout: Timeout = Timeout(10 seconds)
  private[this] val schedule = context.system.scheduler.schedule(Duration.Zero, 30 seconds, self, CheckHealth)
  private[this] val sc = ShadowCloud()

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] val writingChunks = PendingOperation.withRegionChunk
  private[this] val readingChunks = PendingOperation.withRegionChunk
  private[this] var health: StorageHealth = StorageHealth.empty

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
      readingChunks.addWaiter((region, chunk), sender(), { () ⇒
        log.debug("Reading chunk: {}/{}", region, chunk)
        chunkIO ! msg
      })

    case msg: ChunkIODispatcher.Message ⇒
      chunkIO.forward(msg)

    // -----------------------------------------------------------------------
    // Chunk responses
    // -----------------------------------------------------------------------
    case msg @ ChunkIODispatcher.WriteChunk.Success((path, chunk), _) ⇒
      log.debug("Chunk written, appending to index: {}", chunk)
      writingChunks.finish((path, chunk), msg)
      sc.eventStreams.publishStorageEvent(storageId, StorageEvents.ChunkWritten(path, chunk))
      index ! StorageIndex.Envelope(path.region, WriteDiff(IndexDiff.newChunks(chunk.withoutData)))

    case msg @ ChunkIODispatcher.WriteChunk.Failure((region, chunk), error) ⇒
      log.error(error, "Chunk write failure: {}/{}", region, chunk)
      writingChunks.finish((region, chunk), msg)

    case msg: ChunkIODispatcher.ReadChunk.Status ⇒
      readingChunks.finish(msg.key, msg)

    // -----------------------------------------------------------------------
    // Index commands
    // -----------------------------------------------------------------------
    case msg: StorageIndex.Message ⇒
      index.forward(msg)

    // -----------------------------------------------------------------------
    // Storage health
    // -----------------------------------------------------------------------
    case CheckHealth ⇒
      healthProvider.health
        .map(CheckHealth.Success(storageId, _))
        .recover(PartialFunction(CheckHealth.Failure(storageId, _)))
        .pipeTo(self)

    case CheckHealth.Success(`storageId`, health) ⇒
      updateHealth(_ ⇒ health)

    case CheckHealth.Failure(`storageId`, error) ⇒
      updateHealth(_.copy(online = false))
      log.error(error, "Health update failure: {}", storageId)

    // -----------------------------------------------------------------------
    // Storage events
    // -----------------------------------------------------------------------
    case StorageEnvelope(`storageId`, event: StorageEvents.Event) ⇒ event match {
      case StorageEvents.ChunkWritten(_, chunk) ⇒
        val written = chunk.checksum.encSize
        log.debug("{} bytes written, updating storage health", written)
        updateHealth(_ - written)

      case _ ⇒
        // Ignore
    }
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  def updateHealth(func: StorageHealth ⇒ StorageHealth): Unit = {
    this.health = func(this.health)
    log.debug("Storage [{}] health updated: {}", storageId, health)
    sc.eventStreams.publishStorageEvent(storageId, StorageEvents.HealthUpdated(health))
  }

  // -----------------------------------------------------------------------
  // Lifecycle hooks
  // -----------------------------------------------------------------------
  override def preStart(): Unit = {
    super.preStart()
    context.watch(chunkIO)
    context.watch(index)
    sc.eventStreams.storage.subscribe(self, storageId)
  }

  override def postStop(): Unit = {
    sc.eventStreams.storage.unsubscribe(self)
    schedule.cancel()
    super.postStop()
  }
}
