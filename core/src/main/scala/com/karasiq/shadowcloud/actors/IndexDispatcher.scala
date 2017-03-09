package com.karasiq.shadowcloud.actors

import java.util.concurrent.TimeoutException

import akka.Done
import akka.actor.{ActorLogging, DeadLetterSuppression, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.events.StorageEvents._
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.Repository.BaseRepository
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexMerger, IndexRepositoryStreams}
import com.karasiq.shadowcloud.storage.wrappers.RepositoryWrappers

import scala.language.postfixOps
import scala.util.{Failure, Success}

object IndexDispatcher {
  // Messages
  sealed trait Message
  case object GetIndex extends Message {
    case class Success(diffs: Seq[(Long, IndexDiff)], pending: IndexDiff)
  }
  case class AddPending(diff: IndexDiff) extends Message
  object AddPending extends MessageStatus[IndexDiff, IndexDiff]
  case object Synchronize extends Message

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class KeysLoaded(keys: Set[Long]) extends InternalMessage
  private case class ReadSuccess(result: IndexIOResult[Long]) extends InternalMessage
  private case class WriteSuccess(result: IndexIOResult[Long]) extends InternalMessage
  private case object StreamCompleted extends InternalMessage with DeadLetterSuppression

  // Snapshot
  case class Snapshot(diffs: Seq[(Long, IndexDiff)])

  // Props
  def props(storageId: String, repository: BaseRepository, streams: IndexRepositoryStreams = IndexRepositoryStreams.gzipped): Props = {
    Props(classOf[IndexDispatcher], storageId, repository, streams)
  }
}

class IndexDispatcher(storageId: String, repository: BaseRepository, streams: IndexRepositoryStreams)
  extends PersistentActor with ActorLogging {

  import IndexDispatcher._
  import context.dispatcher
  require(storageId.nonEmpty)
  implicit val actorMaterializer = ActorMaterializer()
  val indexRepository = RepositoryWrappers.longSeq(repository)
  val merger = IndexMerger()
  val config = AppConfig().index

  override val persistenceId: String = s"index_$storageId"

  // Local operations
  def receiveDefault: Receive = {
    case GetIndex ⇒
      sender() ! GetIndex.Success(merger.diffs.toVector, merger.pending)

    case AddPending(diff) ⇒
      log.debug("Pending diff added: {}", diff)
      persistAsync(PendingIndexUpdated(diff))(updateState)

    case ReceiveTimeout ⇒
      throw new TimeoutException("Receive timeout")
  }

  // Wait for synchronize command
  def receiveWait: Receive = {
    case Synchronize ⇒
      log.debug("Starting synchronization, last sequence number: {}", merger.lastSequenceNr)
      readRemoteKeys()
  }

  // Loading remote diff keys
  def receivePreRead: Receive = {
    case KeysLoaded(keys) ⇒
      become {
        case StreamCompleted ⇒
          readDiffs(keys)
      }

    case Status.Failure(error) ⇒
      log.error(error, "Diffs load failed")
      throw error
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

    case StreamCompleted ⇒
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

    case StreamCompleted ⇒
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
      context.setReceiveTimeout(config.syncInterval * 10)
  }

  override def receiveCommand: Receive = {
    receiveWait.orElse(receiveDefault)
  }

  private[this] def updateState(event: Event): Unit = {
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
        
      case _ ⇒
        log.warning("Event not handled: {}", event)
    }
  }

  private[this] def become(receive: Receive): Unit = {
    context.become(receive.orElse(receiveDefault))
  }

  private[this] def scheduleSync(): Unit = {
    log.debug("Scheduling synchronize in {}", config.syncInterval.toCoarsest)
    context.system.scheduler.scheduleOnce(config.syncInterval, self, Synchronize)
    become(receiveWait)
  }

  private[this] def readRemoteKeys(): Unit = {
    indexRepository.keys
      .fold(Set.empty[Long])(_ + _)
      .map(KeysLoaded)
      .runWith(Sink.actorRef(self, StreamCompleted))
    become(receivePreRead)
  }

  private[this] def readDiffs(keys: Set[Long]): Unit = {
    def startRead(): Unit = {
      val keySeq = keys.toVector
        .filter(_ > merger.lastSequenceNr)
        .sorted
      if (keySeq.nonEmpty) log.debug("Reading diffs: {}", keySeq)
      Source(keySeq)
        .via(streams.read(indexRepository))
        .map(ReadSuccess)
        .runWith(Sink.actorRef(self, StreamCompleted))
      become(receiveRead)
    }

    val toRemove = merger.diffs.keySet.diff(keys)
    if (toRemove.nonEmpty) {
      log.warning("Diffs evicted: {}", toRemove)
      persistAsync(IndexLoaded(merger.diffs.filterKeys(!toRemove.contains(_)).toVector)) { event ⇒
        updateState(event)
        startRead()
      }
    } else {
      startRead()
    }
  }

  private[this] def write(sequenceNr: Long, diff: IndexDiff): Unit = {
    log.info("Writing diff #{}: {}", sequenceNr, diff)
    Source.single((sequenceNr, diff))
      .via(streams.write(indexRepository))
      .map(WriteSuccess)
      .runWith(Sink.actorRef(self, StreamCompleted))
    become(receiveWrite)
  }
}
