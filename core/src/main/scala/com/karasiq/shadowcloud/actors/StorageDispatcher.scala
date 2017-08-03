package com.karasiq.shadowcloud.actors

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Success

import akka.NotUsed
import akka.actor.{Actor, ActorLogging, ActorRef, Kill, NotInfluenceReceiveTimeout, PossiblyHarmful, Props}
import akka.pattern.pipe
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy, QueueOfferResult}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.actors.ChunkIODispatcher.ChunkPath
import com.karasiq.shadowcloud.index.Chunk
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{StorageHealth, StorageHealthProvider}
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.utils.AkkaStreamUtils

object StorageDispatcher {
  // Messages
  sealed trait Message
  object CheckHealth extends Message with NotInfluenceReceiveTimeout with MessageStatus[String, StorageHealth]

  // Internal messages
  private sealed trait InternalMessage extends PossiblyHarmful
  private case class WriteChunkToIndex(path: ChunkPath, chunk: Chunk) extends InternalMessage

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
  private[this] implicit val materializer: Materializer = ActorMaterializer()
  private[this] val schedule = context.system.scheduler.schedule(Duration.Zero, 30 seconds, self, CheckHealth)
  private[this] val sc = ShadowCloud()

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] var health: StorageHealth = StorageHealth.empty

  // -----------------------------------------------------------------------
  // Streams
  // -----------------------------------------------------------------------
  private[this] val pendingIndexQueue = Source.queue[(ChunkPath, Chunk)](sc.config.queues.chunksIndex, OverflowStrategy.dropNew)
    .via(AkkaStreamUtils.groupedOrInstant(sc.config.queues.chunksIndex, sc.config.queues.chunksIndexTime))
    .mapConcat(_.groupBy(_._1.region).map { case (regionId, chunks) ⇒
      StorageIndex.Envelope(regionId, RegionIndex.WriteDiff(IndexDiff.newChunks(chunks.map(_._2):_*)))
    })
    .to(Sink.actorRef(index, NotUsed))
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
    pendingIndexQueue.watchCompletion().foreach(_ ⇒ self ! Kill)
  }

  override def postStop(): Unit = {
    sc.eventStreams.storage.unsubscribe(self)
    schedule.cancel()
    pendingIndexQueue.complete()
    super.postStop()
  }
}
