package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{ActorLogging, Props, ReceiveTimeout, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import com.karasiq.shadowcloud.actors.events.StorageEvent
import com.karasiq.shadowcloud.actors.events.StorageEvent._
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.IndexRepository.BaseIndexRepository
import com.karasiq.shadowcloud.storage.utils.IndexIOResult
import com.karasiq.shadowcloud.storage.{IndexMerger, IndexRepository, IndexRepositoryStreams}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

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
  private case class ReadSuccess(result: IndexIOResult[Long]) extends Message
  private case object CompleteRead extends Message
  private case class WriteSuccess(result: IndexIOResult[Long]) extends Message
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
        .map(ReadSuccess)
        .runWith(Sink.actorRef(self, CompleteRead))
      context.become(receiveRead.orElse(receiveDefault))

    case ReceiveTimeout ⇒
      throw new IllegalArgumentException("Receive timeout")
  }

  // Reading remote diffs
  def receiveRead: Receive = {
    case ReadSuccess(IndexIOResult(sequenceNr, diff, IOResult(bytes, ioResult))) ⇒ ioResult match {
      case Success(Done) ⇒
        log.info("Remote diff #{} received, {} bytes: {}", sequenceNr, bytes, diff)
        persistAsync(IndexUpdated(sequenceNr, diff, remote = true))(updateState)

      case Failure(error) ⇒ 
        log.error(error, "Diff #{} read failed", sequenceNr)
        throw error
    }

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
    case WriteSuccess(IndexIOResult(sequenceNr, diff, IOResult(bytes, ioResult))) ⇒ ioResult match {
      case Success(Done) ⇒
        log.info("Diff #{} written, {} bytes: {}", sequenceNr, bytes, diff)
        persistAsync(IndexUpdated(sequenceNr, diff, remote = false))(updateState)

      case Failure(error) ⇒
        log.error(error, "Diff #{} write error: {}", sequenceNr, diff)
    }

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
        merger.addPending(IndexDiff.newChunks(chunk.withoutData))

      case _ ⇒
        log.warning("Event not handled: {}", event)
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
      .map(WriteSuccess)
      .runWith(Sink.actorRef(self, CompleteWrite))
    context.become(receiveWrite.orElse(receiveDefault))
  }
}
