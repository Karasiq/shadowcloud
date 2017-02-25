package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{ActorLogging, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.events.StorageEvents._
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.IndexRepository
import com.karasiq.shadowcloud.storage.IndexRepository.BaseIndexRepository
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexMerger, IndexRepositoryStreams}

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{Failure, Success}

object IndexDispatcher {
  // Messages
  sealed trait Message
  case object GetIndex extends Message {
    case class Success(diffs: Seq[(Long, IndexDiff)])
  }
  case class AddPending(diff: IndexDiff) extends Message
  object AddPending extends MessageStatus[IndexDiff, IndexDiff]
  case object Synchronize extends Message

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class ReadSuccess(result: IndexIOResult[Long]) extends InternalMessage
  private case object CompleteRead extends InternalMessage
  private case class WriteSuccess(result: IndexIOResult[Long]) extends InternalMessage
  private case object CompleteWrite extends InternalMessage

  // Snapshot
  case class Snapshot(diffs: Seq[(Long, IndexDiff)])

  // Props
  def props(storageId: String, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams = IndexRepositoryStreams.gzipped): Props = {
    Props(classOf[IndexDispatcher], storageId, baseIndexRepository, streams)
  }
}

class IndexDispatcher(storageId: String, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams) extends PersistentActor with ActorLogging {
  import IndexDispatcher._
  import context.dispatcher
  require(storageId.nonEmpty)
  implicit val actorMaterializer = ActorMaterializer()
  val indexRepository = IndexRepository.numeric(baseIndexRepository)
  val merger = IndexMerger()
  val config = AppConfig().index

  override val persistenceId: String = s"index_$storageId"

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
      // TODO: Reload remote keys
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
        log.debug("Diff #{} written, {} bytes: {}", sequenceNr, bytes, diff)
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
    case SnapshotOffer(_, Snapshot(diffs)) ⇒ // TODO: Snapshots
      log.debug("Loading snapshot with sequence number: {}", diffs.lastOption.fold(0L)(_._1))
      updateState(IndexLoaded(diffs))

    case event: Event ⇒
      updateState(event)

    case RecoveryCompleted ⇒
      context.system.scheduler.scheduleOnce(config.syncInterval, self, Synchronize)
      context.setReceiveTimeout(2 minutes)
  }

  override def receiveCommand: Receive = {
    receiveWait.orElse(receiveDefault)
  }

  def updateState(event: Event): Unit = {
    StorageEvents.stream.publish(StorageEnvelope(storageId, event))
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

      // case ChunkWritten(chunk) ⇒
      //   merger.addPending(IndexDiff.newChunks(chunk.withoutData))

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
