package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, NotInfluenceReceiveTimeout, PossiblyHarmful, Props, Status}
import akka.pattern.pipe
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.utils.StorageHealth
import com.karasiq.shadowcloud.model.{Chunk, StorageId}
import com.karasiq.shadowcloud.providers.LifecycleHook
import com.karasiq.shadowcloud.storage.StorageHealthProvider
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils

import scala.concurrent.duration._
import scala.util.{Success, Try}

object StorageDispatcher {
  // Messages
  sealed trait Message
  final case class GetHealth(checkNow: Boolean = false) extends Message with NotInfluenceReceiveTimeout
  object GetHealth                                      extends MessageStatus[StorageId, StorageHealth]

  // Internal messages
  private sealed trait InternalMessage                                extends Message with PossiblyHarmful
  private case class WriteChunkToIndex(path: ChunkPath, chunk: Chunk) extends InternalMessage

  // Props
  def props(
      storageId: StorageId,
      storageProps: StorageProps,
      index: ActorRef,
      chunkIO: ActorRef,
      health: StorageHealthProvider,
      lifecycleHook: Option[LifecycleHook] = None
  ): Props = {
    Props(new StorageDispatcher(storageId, storageProps, index, chunkIO, health, lifecycleHook))
  }
}

private final class StorageDispatcher(
    storageId: StorageId,
    storageProps: StorageProps,
    index: ActorRef,
    chunkIO: ActorRef,
    healthProvider: StorageHealthProvider,
    lifecycleHook: Option[LifecycleHook] = None
) extends Actor
    with ActorLogging {
  import StorageDispatcher._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  import context.dispatcher

  private[this] val sc     = ShadowCloud()
  private[this] val config = sc.configs.storageConfig(storageId, storageProps)
  private[this] val healthCheckSchedule =
    context.system.scheduler.scheduleWithFixedDelay(Duration.Zero, config.healthCheckInterval, self, GetHealth(true))

  import sc.implicits.materializer

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] var health: StorageHealth = StorageHealth.empty

  // -----------------------------------------------------------------------
  // Streams
  // -----------------------------------------------------------------------
  private[this] val pendingIndexQueue = Source
    .queue[(ChunkPath, Chunk)](sc.config.queues.chunksIndex, OverflowStrategy.dropNew)
    .via(AkkaStreamUtils.groupedOrInstant(sc.config.queues.chunksIndex, sc.config.queues.chunksIndexTime))
    .filter(_.nonEmpty)
    .mapConcat(_.groupBy(_._1.regionId).map {
      case (regionId, chunks) ⇒
        StorageIndex.Envelope(regionId, RegionIndex.WriteDiff(IndexDiff.newChunks(chunks.map(_._2): _*)))
    })
    .withAttributes(ActorAttributes.supervisionStrategy(Supervision.resumingDecider))
    .to(Sink.actorRef(index, Status.Failure(new IllegalStateException("Queue stopped"))))
    .named("pendingIndexQueue")
    .run()

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    // -----------------------------------------------------------------------
    // Chunk commands
    // -----------------------------------------------------------------------
    case msg: ChunkIODispatcher.Message ⇒
      chunkIO.forward(msg)

    // -----------------------------------------------------------------------
    // Index commands
    // -----------------------------------------------------------------------
    case msg: StorageIndex.Message ⇒
      index.forward(msg)

    case WriteChunkToIndex(path, chunk) ⇒
      val scheduler = context.system.scheduler
      pendingIndexQueue.offer((path, chunk)).onComplete {
        case Success(QueueOfferResult.Enqueued) ⇒
        // Pass

        case _ ⇒
          log.warning("Rescheduling chunk index write: {}/{}", path, chunk)
          scheduler.scheduleOnce(15 seconds, sc.actors.regionSupervisor, StorageEnvelope(storageId, WriteChunkToIndex(path, chunk)))
      }

    // -----------------------------------------------------------------------
    // Storage health
    // -----------------------------------------------------------------------
    case GetHealth(checkNow) ⇒
      if (checkNow) {
        val future = GetHealth.wrapFuture(storageId, healthProvider.health)
        future.pipeTo(self).pipeTo(sender())
      } else {
        sender() ! GetHealth.Success(storageId, health)
      }

    case GetHealth.Success(`storageId`, health) ⇒
      updateHealth(_ ⇒ health)

    case GetHealth.Failure(`storageId`, error) ⇒
      updateHealth(_.copy(online = false))
      log.error(error, "Health update failure: {}", storageId)

    // -----------------------------------------------------------------------
    // Storage events
    // -----------------------------------------------------------------------
    case StorageEnvelope(`storageId`, event: StorageEvents.Event) ⇒
      event match {
        case StorageEvents.ChunkWritten(path, chunk) ⇒
          log.debug("Appending new chunk to index: {}", chunk)
          self ! WriteChunkToIndex(path, chunk.withoutData)

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
    lifecycleHook.foreach(_.initialize())
  }

  override def postStop(): Unit = {
    Try(lifecycleHook.foreach(_.shutdown())).failed.foreach(log.error(_, "Error in shutdown hook"))
    sc.eventStreams.storage.unsubscribe(self)
    healthCheckSchedule.cancel()
    pendingIndexQueue.complete()
    super.postStop()
  }
}
