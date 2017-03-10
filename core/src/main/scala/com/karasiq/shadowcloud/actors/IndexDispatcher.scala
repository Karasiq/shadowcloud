package com.karasiq.shadowcloud.actors

import java.util.concurrent.TimeoutException

import akka.Done
import akka.actor.{ActorLogging, DeadLetterSuppression, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.events.StorageEvents._
import com.karasiq.shadowcloud.actors.internal.MultiIndexMerger
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.CategorizedRepository
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexRepositoryStreams}

import scala.collection.SortedMap
import scala.language.postfixOps
import scala.util.{Failure, Success}

object IndexDispatcher {
  private type LocalKey = (String, Long)

  // Messages
  sealed trait Message
  case class GetIndex(region: String) extends Message
  object GetIndex {
    case class Success(diffs: Seq[(Long, IndexDiff)], pending: IndexDiff)
  }
  case class AddPending(region: String, diff: IndexDiff) extends Message
  object AddPending extends MessageStatus[IndexDiff, IndexDiff]
  case object Synchronize extends Message

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class KeysLoaded(keys: Set[LocalKey]) extends InternalMessage
  private case class ReadSuccess(result: IndexIOResult[LocalKey]) extends InternalMessage
  private case class WriteSuccess(result: IndexIOResult[LocalKey]) extends InternalMessage
  private case object StreamCompleted extends InternalMessage with DeadLetterSuppression

  // Snapshot
  case class Snapshot(diffs: Map[String, SortedMap[Long, IndexDiff]])

  // Props
  def props(storageId: String, repository: CategorizedRepository[String, Long], streams: IndexRepositoryStreams = IndexRepositoryStreams.gzipped): Props = {
    Props(classOf[IndexDispatcher], storageId, repository, streams)
  }
}

private final class IndexDispatcher(storageId: String, repository: CategorizedRepository[String, Long], streams: IndexRepositoryStreams)
  extends PersistentActor with ActorLogging {

  import IndexDispatcher._
  import context.dispatcher
  require(storageId.nonEmpty)
  implicit val actorMaterializer = ActorMaterializer()
  val index = MultiIndexMerger()
  val config = AppConfig().index

  override val persistenceId: String = s"index_$storageId"

  // Local operations
  def receiveDefault: Receive = {
    case GetIndex(region) ⇒
      sender() ! GetIndex.Success(index.diffs(region).toVector, index.pending(region))

    case AddPending(region, diff) ⇒
      log.debug("Pending diff added: {}", diff)
      persistAsync(PendingIndexUpdated(region, diff))(updateState)

    case ReceiveTimeout ⇒
      throw new TimeoutException("Receive timeout")
  }

  // Wait for synchronize command
  def receiveWait: Receive = {
    case Synchronize ⇒
      log.debug("Starting synchronization")
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
    case ReadSuccess(IndexIOResult((region, sequenceNr), diff, IOResult(bytes, ioResult))) ⇒ ioResult match {
      case Success(Done) ⇒
        log.info("Remote diff {}/{} received, {} bytes: {}", region, sequenceNr, bytes, diff)
        persistAsync(IndexUpdated(region, sequenceNr, diff, remote = true))(updateState)

      case Failure(error) ⇒
        log.error(error, "Diff #{} read failed", sequenceNr)
        throw error
    }

    case Status.Failure(error) ⇒
      throw error

    case StreamCompleted ⇒
      log.debug("Synchronization read completed")
      val pending = for ((region, index) ← index.subIndexes.toVector if index.pending.nonEmpty)
        yield ((region, index.lastSequenceNr + 1), index.pending)
      if (pending.nonEmpty) {
        write(pending)
      } else {
        scheduleSync()
      }
  }

  // Writing local diffs
  def receiveWrite: Receive = {
    case WriteSuccess(IndexIOResult((region, sequenceNr), diff, IOResult(bytes, ioResult))) ⇒ ioResult match {
      case Success(Done) ⇒
        log.debug("Diff {}/{} written, {} bytes: {}", region, sequenceNr, bytes, diff)
        persistAsync(IndexUpdated(region, sequenceNr, diff, remote = false))(updateState)

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
    case SnapshotOffer(metadata, Snapshot(diffs)) ⇒ // TODO: Save snapshots
      log.debug("Loading snapshot: {}", metadata)
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
      case IndexUpdated(region, sequenceNr, diff, _) ⇒
        index.add(region, sequenceNr, diff)

      case PendingIndexUpdated(region, diff) ⇒
        index.addPending(region, diff)

      case IndexLoaded(diffs) ⇒
        index.clear()
        for ((region, subDiffs) ← diffs; (sequenceNr, diff) ← subDiffs) {
          index.add(region, sequenceNr, diff)
        }

      case IndexDeleted(region, sequenceNrs) ⇒
        index.delete(region, sequenceNrs)

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
    repository.keys
      .fold(Set.empty[(String, Long)])(_ + _)
      .map(KeysLoaded)
      .runWith(Sink.actorRef(self, StreamCompleted))
    become(receivePreRead)
  }

  private[this] def readDiffs(keys: Set[LocalKey]): Unit = {
    def startRead(): Unit = {
      val keySeq = keys.filter { case (region, sequenceNr) ⇒ sequenceNr > index.lastSequenceNr(region) }
        .toVector
        .sorted
      if (keySeq.nonEmpty) log.debug("Reading diffs: {}", keySeq)
      Source(keySeq)
        .via(streams.read(repository))
        .map(ReadSuccess)
        .runWith(Sink.actorRef(self, StreamCompleted))
      become(receiveRead)
    }

    val deletedDiffs = index.subDiffs.map { case (region, diffs) ⇒
      val existing = keys.filter(_._1 == region).map(_._2)
      region → diffs.keySet.diff(existing)
    }.filter(_._2.nonEmpty)

    val deleteEvents = {
      deletedDiffs
        .map { case (region, sequenceNrs) ⇒ IndexDeleted(region, sequenceNrs.toSet) }
        .toVector
    }

    if (deleteEvents.nonEmpty) {
      log.warning("Diffs deleted: {}", deletedDiffs)
      persistAllAsync(deleteEvents)(updateState)
      deferAsync(Unit)(_ ⇒ startRead())
    } else {
      startRead()
    }
  }

  private[this] def write(diffs: Seq[((String, Long), IndexDiff)]): Unit = {
    log.debug("Writing pending diffs: {}", diffs)
    Source(diffs.toVector)
      .alsoTo(Sink.foreach { case ((region, sequenceNr), diff) ⇒
        log.info("Writing diff {}/{}: {}", region, sequenceNr, diff)
      })
      .via(streams.write(repository))
      .map(WriteSuccess)
      .runWith(Sink.actorRef(self, StreamCompleted))
    become(receiveWrite)
  }
}
