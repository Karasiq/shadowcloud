package com.karasiq.shadowcloud.actors

import java.util.concurrent.TimeoutException

import akka.NotUsed
import akka.actor.{ActorLogging, DeadLetterSuppression, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.persistence._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.actors.events.StorageEvents
import com.karasiq.shadowcloud.actors.events.StorageEvents._
import com.karasiq.shadowcloud.actors.internal.MultiIndexMerger
import com.karasiq.shadowcloud.actors.messages.StorageEnvelope
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.config.AppConfig
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexMerger, IndexRepositoryStreams}
import com.karasiq.shadowcloud.storage.{CategorizedRepository, StorageIOResult}

import scala.collection.mutable.{Set => MSet}
import scala.concurrent.duration._
import scala.language.postfixOps

object IndexDispatcher {
  private type LocalKey = (String, Long)

  // Messages
  sealed trait Message
  case object GetIndexes extends Message with MessageStatus[String, Map[String, IndexMerger.State[Long]]]
  case class GetIndex(region: String) extends Message
  object GetIndex extends MessageStatus[String, IndexMerger.State[Long]]
  case class AddPending(region: String, diff: IndexDiff) extends Message
  object AddPending extends MessageStatus[IndexDiff, IndexDiff]
  case class CompactIndex(region: String) extends Message
  case object Synchronize extends Message

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class KeysLoaded(keys: Set[LocalKey]) extends InternalMessage
  private case class ReadSuccess(result: IndexIOResult[LocalKey]) extends InternalMessage
  private case class WriteSuccess(result: IndexIOResult[LocalKey]) extends InternalMessage
  private case class CompactSuccess(deleted: Set[Long], result: IndexIOResult[LocalKey]) extends InternalMessage
  private case object StreamCompleted extends InternalMessage with DeadLetterSuppression

  // Snapshot
  private case class Snapshot(diffs: Map[String, IndexMerger.State[Long]])

  // Props
  def props(storageId: String, repository: CategorizedRepository[String, Long]): Props = {
    Props(classOf[IndexDispatcher], storageId, repository)
  }
}

private final class IndexDispatcher(storageId: String, repository: CategorizedRepository[String, Long])
  extends PersistentActor with ActorLogging {
  import IndexDispatcher._
  import context.dispatcher

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  require(storageId.nonEmpty)
  override val persistenceId: String = s"index_$storageId"
  implicit val actorMaterializer = ActorMaterializer()
  val streams = IndexRepositoryStreams.gzipped(context.system)
  val index = MultiIndexMerger()
  val config = AppConfig().index
  val compactRequested = MSet.empty[String]

  // -----------------------------------------------------------------------
  // Local operations
  // -----------------------------------------------------------------------
  def receiveDefault: Receive = {
    case GetIndexes ⇒
      val states = index.subIndexes.mapValues(IndexMerger.state)
      sender() ! GetIndexes.Success(storageId, states)

    case GetIndex(region) ⇒
      index.subIndexes.get(region) match {
        case Some(index) ⇒
          sender() ! GetIndex.Success(region, IndexMerger.state(index))

        case None ⇒
          sender() ! GetIndex.Failure(region, new NoSuchElementException(region))
      }

    case AddPending(region, diff) ⇒
      log.debug("Pending diff added: {}", diff)
      persist(PendingIndexUpdated(region, diff))(updateState)

    case CompactIndex(region) ⇒
      if (!compactRequested.contains(region)) {
        log.debug("Index compaction requested: {}", region)
        compactRequested += region
      }

    case ReceiveTimeout ⇒
      throw new TimeoutException("Receive timeout")
  }

  // -----------------------------------------------------------------------
  // Idle state
  // -----------------------------------------------------------------------
  def receiveWait: Receive = {
    case Synchronize ⇒
      log.debug("Starting synchronization")
      deferAsync(NotUsed)(_ ⇒ startSync())
  }

  // -----------------------------------------------------------------------
  // Pre-read state
  // -----------------------------------------------------------------------
  def receivePreRead(loadedKeys: Set[LocalKey] = Set.empty): Receive = {
    case KeysLoaded(keys) ⇒
      become(receivePreRead(loadedKeys ++ keys))

    case Status.Failure(error) ⇒
      log.error(error, "Diffs load failed")
      throw error

    case StreamCompleted ⇒
      deferAsync(NotUsed)(_ ⇒ startRead(loadedKeys))
  }

  // -----------------------------------------------------------------------
  // Read state
  // -----------------------------------------------------------------------
  def receiveRead: Receive = {
    case ReadSuccess(IndexIOResult((region, sequenceNr), diff, ioResult)) ⇒ ioResult match {
      case StorageIOResult.Success(path, _) ⇒
        log.info("Remote diff {}/{} received from {}: {}", region, sequenceNr, path, diff)
        persist(IndexUpdated(region, sequenceNr, diff, remote = true))(updateState)

      case StorageIOResult.Failure(path, error) ⇒
        log.error(error, "Diff {}/{} read failed from {}", region, sequenceNr, path)
        throw error
    }

    case Status.Failure(error) ⇒
      log.error(error, "Diff read failed")
      throw error

    case StreamCompleted ⇒
      log.debug("Synchronization read completed")
      deferAsync(NotUsed)(_ ⇒ startWrite())
  }

  // -----------------------------------------------------------------------
  // Write state
  // -----------------------------------------------------------------------
  def receiveWrite: Receive = {
    case WriteSuccess(IndexIOResult((region, sequenceNr), diff, ioResult)) ⇒ ioResult match {
      case StorageIOResult.Success(path, _) ⇒
        log.debug("Diff {}/{} written to {}: {}", region, sequenceNr, path, diff)
        persist(IndexUpdated(region, sequenceNr, diff, remote = false))(updateState)

      case StorageIOResult.Failure(path, error) ⇒
        log.error(error, "Diff {}/{} write error to {}: {}", region, sequenceNr, path, diff)
    }

    case Status.Failure(error) ⇒
      log.error(error, "Write error")
      scheduleSync()

    case StreamCompleted ⇒
      log.debug("Synchronization write completed")
      deferAsync(NotUsed)(_ ⇒ startCompact())
  }

  // -----------------------------------------------------------------------
  // Compact state
  // -----------------------------------------------------------------------
  def receiveCompact: Receive = {
    case CompactSuccess(deleted, result) ⇒
      log.info("Compact success: deleted = {}, new = {}", deleted, result)
      if (result.diff.isEmpty) {
        persist(IndexDeleted(result.key._1, deleted))(updateState)
      } else {
        persistAll(List(
          IndexDeleted(result.key._1, deleted),
          IndexUpdated(result.key._1, result.key._2, result.diff, remote = false)
        ))(updateState)
      }

    case Status.Failure(error) ⇒
      log.error(error, "Compact error")
      scheduleSync()

    case StreamCompleted ⇒
      log.debug("Indexes compaction completed")
      compactRequested.clear()
      deferAsync(NotUsed)(_ ⇒ createSnapshot(() ⇒ scheduleSync()))
  }

  // -----------------------------------------------------------------------
  // Default receive
  // -----------------------------------------------------------------------
  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata, Snapshot(diffs)) ⇒ // TODO: Save snapshots
      log.debug("Loading snapshot: {}", metadata)
      updateState(IndexLoaded(diffs))

    case event: Event ⇒
      updateState(event)

    case RecoveryCompleted ⇒
      scheduleSync(1 second) // Initial sync
      context.setReceiveTimeout(config.syncInterval * 10)
  }

  override def receiveCommand: Receive = {
    receiveWait.orElse(receiveDefault)
  }

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] def updateState(event: Event): Unit = {
    StorageEvents.stream.publish(StorageEnvelope(storageId, event))
    event match {
      case IndexUpdated(region, sequenceNr, diff, _) ⇒
        index.add(region, sequenceNr, diff)

      case PendingIndexUpdated(region, diff) ⇒
        index.addPending(region, diff)

      case IndexLoaded(diffs) ⇒
        index.clear()
        for ((region, state) ← diffs) {
          index.addPending(region, state.pending)
          for ((sequenceNr, diff) ← state.diffs) index.add(region, sequenceNr, diff)
        }

      case IndexDeleted(region, sequenceNrs) ⇒
        index.delete(region, sequenceNrs)

      case _ ⇒
        log.warning("Event not handled: {}", event)
    }
  }

  // -----------------------------------------------------------------------
  // Synchronization
  // -----------------------------------------------------------------------
  private[this] def startSync(): Unit = {
    repository.keys
      .fold(Set.empty[(String, Long)])(_ + _)
      .map(KeysLoaded)
      .runWith(Sink.actorRef(self, StreamCompleted))
    become(receivePreRead())
  }

  private[this] def startRead(keys: Set[LocalKey]): Unit = {
    def becomeRead(keys: Set[LocalKey]): Unit = {
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
      persistAll(deleteEvents)(updateState)
    }
    deferAsync(Unit)(_ ⇒ becomeRead(keys))
  }

  private[this] def startWrite(): Unit = {
    def becomeWrite(diffs: Seq[(LocalKey, IndexDiff)]): Unit = {
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
    val pending = for ((region, index) ← index.subIndexes.toVector if index.pending.nonEmpty)
      yield ((region, index.lastSequenceNr + 1), index.pending)
    if (pending.nonEmpty) {
      becomeWrite(pending)
    } else {
      scheduleSync()
    }
  }

  private[this] def startCompact(): Unit = {
    def becomeCompact(regions: Seq[String]): Unit = {
      def compactIndex(region: String, index: IndexMerger[Long]): Source[CompactSuccess, NotUsed] = {
        log.debug("Compacting diffs: {}", index.diffs)
        val diffs = index.diffs.toVector
        val newDiff = index.mergedDiff
        val newSequenceNr = index.lastSequenceNr + 1
        log.debug("Writing compacted diff: {}/{} -> {}", region, newSequenceNr, newDiff)
        val writeResult = if (newDiff.isEmpty) {
          // Skip write
          val empty = IndexIOResult((region, newSequenceNr), IndexDiff.empty, StorageIOResult.Success("", 0))
          Source.single(empty)
        } else {
          Source.single((region, newSequenceNr) → newDiff)
            .via(streams.write(repository))
            .log("compact-write")
        }

        writeResult.flatMapConcat(wr ⇒ wr.ioResult match {
          case StorageIOResult.Success(_, _) ⇒
            Source(diffs)
              .map(kv ⇒ (region, kv._1))
              .via(streams.delete(repository))
              .log("compact-delete")
              .filter(_.ioResult.isSuccess)
              .map(_.key._2)
              .fold(Set.empty[Long])(_ + _)
              .map(CompactSuccess(_, wr))

          case StorageIOResult.Failure(_, error) ⇒
            Source.failed(error)
        })
      }
      val sources = regions
        .filter(r ⇒ index.subIndexes.get(r).exists(_.diffs.size > 1))
        .map(r ⇒ compactIndex(r, index.subIndexes(r)))

      Source(sources.toVector)
        .flatMapConcat(identity)
        .runWith(Sink.actorRef(self, StreamCompleted))

      become(receiveCompact)
    }
    if (compactRequested.isEmpty) {
      scheduleSync()
    } else {
      becomeCompact(compactRequested.toVector)
    }
  }

  // -----------------------------------------------------------------------
  // Snapshots
  // -----------------------------------------------------------------------
  private[this] def createSnapshot(after: () ⇒ Unit): Unit = {
    saveSnapshot(Snapshot(index.subIndexes.mapValues(IndexMerger.state)))
    become {
      case SaveSnapshotSuccess(snapshot) ⇒
        log.debug("Snapshot saved: {}", snapshot)
        after()

      case SaveSnapshotFailure(snapshot, error) ⇒
        log.error(error, "Snapshot saving failed: {}", snapshot)
        after()
    }
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def scheduleSync(duration: FiniteDuration = config.syncInterval): Unit = {
    if (log.isDebugEnabled) log.debug("Scheduling synchronize in {}", duration.toCoarsest)
    context.system.scheduler.scheduleOnce(duration, self, Synchronize)
    become(receiveWait)
  }

  private[this] def become(receive: Receive): Unit = {
    context.become(receive.orElse(receiveDefault))
  }
}
