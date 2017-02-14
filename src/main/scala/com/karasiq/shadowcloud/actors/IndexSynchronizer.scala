package com.karasiq.shadowcloud.actors

import akka.actor.{ActorLogging, Props, ReceiveTimeout, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent._
import com.karasiq.shadowcloud.actors.internal.MessageStatus
import com.karasiq.shadowcloud.index.{ChunkIndexDiff, IndexDiff}
import com.karasiq.shadowcloud.storage.{BaseIndexRepository, IndexMerger, IndexRepository, IndexRepositoryStreams}
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.duration._
import scala.language.postfixOps

object IndexSynchronizer {
  // Messages
  sealed trait Message
  case object Get extends Message
  case class Append(diff: IndexDiff) extends Message
  object Append extends MessageStatus[IndexDiff, IndexDiff]
  case object Synchronize extends Message

  // Internal messages
  private case class ReadSuccess(sequenceNr: Long, indexDiff: IndexDiff) extends Message
  private case object CompleteRead extends Message
  private case class WriteSuccess(sequenceNr: Long, diff: IndexDiff) extends Message
  private case object CompleteWrite extends Message

  // Snapshot
  case class Snapshot(diffs: Seq[(Long, IndexDiff)])

  // Props
  def props(indexId: String, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams = IndexRepositoryStreams.default): Props = {
    Props(classOf[IndexSynchronizer], indexId, baseIndexRepository, streams)
  }
}

class IndexSynchronizer(indexId: String, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams) extends PersistentActor with ActorLogging {
  import IndexSynchronizer._
  import context.dispatcher
  require(indexId.nonEmpty)
  implicit val actorMaterializer = ActorMaterializer()
  val indexRepository = IndexRepository.incremental(baseIndexRepository)
  val merger = IndexMerger()
  val syncInterval = Utils.scalaDuration(Utils.config.getDuration("shadowcloud.index.sync-interval"))

  def persistenceId: String = s"index_$indexId"

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
    log.debug("Scheduling synchronize in {}", syncInterval)
    context.system.scheduler.scheduleOnce(syncInterval, self, Synchronize)
    context.become(receiveWait)
  }

  def write(sequenceNr: Long, diff: IndexDiff): Unit = {
    log.debug("Writing diff #{}: {}", sequenceNr, diff)
    Source.single((sequenceNr, diff))
      .via(streams.write(indexRepository))
      .map(WriteSuccess.tupled)
      .runWith(Sink.actorRef(self, CompleteWrite))
    context.become(receiveWrite)
  }

  def receiveDefault: Receive = {
    case Get ⇒
      sender() ! IndexLoaded(merger.diffs.toVector)

    case Append(diff) ⇒
      persistAsync(PendingIndexUpdated(diff))(updateState)
  }

  def receiveWait: Receive = {
    case Synchronize ⇒
      log.debug("Starting synchronization, last sequence number: {}", merger.lastSequenceNr)
      indexRepository.keysAfter(merger.lastSequenceNr)
        .via(streams.read(indexRepository))
        .map(ReadSuccess.tupled)
        .runWith(Sink.actorRef(self, CompleteRead))
      context.become(receiveRead)

    case ReceiveTimeout ⇒
      throw new IllegalArgumentException("Receive timeout")
  }

  def receiveRead: Receive = {
    case ReadSuccess(sequenceNr, diff) ⇒
      log.info("Remote diff received: {}", diff)
      persistAsync(IndexUpdated(sequenceNr, diff, remote = true))(updateState)

    case Status.Failure(error) ⇒
      throw error

    case CompleteRead ⇒
      if (merger.pending.nonEmpty) {
        write(merger.lastSequenceNr + 1, merger.pending)
      } else {
        scheduleSync()
      }
  }

  def receiveWrite: Receive = {
    case WriteSuccess(sequenceNr, diff) ⇒
      log.info("Diff written: {}", diff)
      persistAsync(IndexUpdated(sequenceNr, diff, remote = false))(updateState)

    case Status.Failure(error) ⇒
      log.error(error, "Write error")
      scheduleSync()

    case CompleteWrite ⇒
      scheduleSync()
  }

  def receiveRecover: Receive = {
    case SnapshotOffer(_, Snapshot(diffs)) ⇒
      log.debug("Loading snapshot with sequence number: {}", diffs.lastOption.fold(0L)(_._1))
      updateState(IndexLoaded(diffs))

    case event: Event ⇒
      updateState(event)

    case RecoveryCompleted ⇒
      context.system.scheduler.scheduleOnce(syncInterval, self, Synchronize)
      context.become(receiveDefault)
      context.become(receiveWait, discardOld = false)
      context.setReceiveTimeout(2 minutes)
  }

  def receiveCommand: Receive = receiveDefault
}
