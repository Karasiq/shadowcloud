package com.karasiq.shadowcloud.actors

import akka.actor.{ActorLogging, Props, ReceiveTimeout, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent._
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.{ChunkIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage.{BaseIndexRepository, IndexMerger, IndexRepository, IndexRepositoryStreams}
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.duration._
import scala.language.postfixOps

object IndexSynchronizer {
  // Messages
  sealed trait Message
  case object GetIndex extends Message {
    case class Success(diffs: Seq[(Long, IndexDiff)])
  }
  case class AddPending(diff: IndexDiff) extends Message
  object AddPending extends MessageStatus[IndexDiff, IndexDiff]
  case object Synchronize extends Message

  // Internal messages
  private case class ReadSuccess(sequenceNr: Long, indexDiff: IndexDiff) extends Message
  private case object CompleteRead extends Message
  private case class WriteSuccess(sequenceNr: Long, diff: IndexDiff) extends Message
  private case object CompleteWrite extends Message

  // Snapshot
  case class Snapshot(diffs: Seq[(Long, IndexDiff)])

  // Props
  def props(indexId: String, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams = IndexRepositoryStreams.gzipped): Props = {
    Props(classOf[IndexSynchronizer], indexId, baseIndexRepository, streams)
  }
}

class IndexSynchronizer(indexId: String, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams) extends PersistentActor with ActorLogging {
  import IndexSynchronizer._
  import context.dispatcher
  require(indexId.nonEmpty)
  implicit val actorMaterializer = ActorMaterializer()
  val indexRepository = IndexRepository.numeric(baseIndexRepository)
  val merger = IndexMerger()
  val config = AppConfig().index

  override val persistenceId: String = s"index_$indexId"

  // Local operations
  def receiveDefault: Receive = {
    case GetIndex ⇒
      sender() ! GetIndex.Success(merger.diffs.toVector)

    case AddPending(diff) ⇒
      log.debug("Pending diff added: {}", diff)
      persistAsync(PendingIndexUpdated(diff))(updateState)
  }

  // Wait for synchronize command
  def receiveWait: Receive = {
    case Synchronize ⇒
      log.debug("Starting synchronization, last sequence number: {}", merger.lastSequenceNr)
      indexRepository.keysAfter(merger.lastSequenceNr)
        .via(streams.read(indexRepository))
        .map(ReadSuccess.tupled)
        .runWith(Sink.actorRef(self, CompleteRead))
      context.become(receiveRead.orElse(receiveDefault))

    case ReceiveTimeout ⇒
      throw new IllegalArgumentException("Receive timeout")
  }

  // Reading remote diffs
  def receiveRead: Receive = {
    case ReadSuccess(sequenceNr, diff) ⇒
      log.info("Remote diff received: {}", diff)
      persistAsync(IndexUpdated(sequenceNr, diff, remote = true))(updateState)

    case Status.Failure(error) ⇒
      throw error

    case CompleteRead ⇒
      log.debug("Synchronization read completed")
      if (merger.pending.nonEmpty) {
        write(merger.lastSequenceNr + 1, merger.pending)
      } else {
        scheduleSync()
      }
  }

  // Writing local diffs
  def receiveWrite: Receive = {
    case WriteSuccess(sequenceNr, diff) ⇒
      log.info("Diff written: {}", diff)
      persistAsync(IndexUpdated(sequenceNr, diff, remote = false))(updateState)

    case Status.Failure(error) ⇒
      log.error(error, "Write error")
      scheduleSync()

    case CompleteWrite ⇒
      log.debug("Synchronization write completed")
      scheduleSync()
  }

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, Snapshot(diffs)) ⇒
      log.debug("Loading snapshot with sequence number: {}", diffs.lastOption.fold(0L)(_._1))
      updateState(IndexLoaded(diffs))

    case event: Event ⇒
      updateState(event)

    case RecoveryCompleted ⇒
      context.system.scheduler.scheduleOnce(config.syncInterval, self, Synchronize)
      context.setReceiveTimeout(2 minutes)
  }

  override def receiveCommand: Receive = receiveWait.orElse(receiveDefault)

  def updateState(event: Event): Unit = {
    StorageEvent.stream.publish(StorageEvent.StorageEnvelope(indexId, event))
    event match {
      case IndexUpdated(sequenceNr, diff, _) ⇒
        merger.add(sequenceNr, diff)

      case PendingIndexUpdated(diff) ⇒
        merger.addPending(diff)

      case IndexLoaded(diffs) ⇒
        merger.clear()
        for ((sequenceNr, diff) ← diffs) {
          merger.add(sequenceNr, diff)
        }

      case ChunkWritten(chunk) ⇒
        merger.addPending(IndexDiff(Utils.timestamp, chunks = ChunkIndexDiff(newChunks = Set(chunk.withoutData))))
    }
  }

  def scheduleSync(): Unit = {
    log.debug("Scheduling synchronize in {}", config.syncInterval.toCoarsest)
    context.system.scheduler.scheduleOnce(config.syncInterval, self, Synchronize)
    context.become(receiveWait.orElse(receiveDefault))
  }

  def write(sequenceNr: Long, diff: IndexDiff): Unit = {
    log.info("Writing diff #{}: {}", sequenceNr, diff)
    Source.single((sequenceNr, diff))
      .via(streams.write(indexRepository))
      .map(WriteSuccess.tupled)
      .runWith(Sink.actorRef(self, CompleteWrite))
    context.become(receiveWrite.orElse(receiveDefault))
  }
}
