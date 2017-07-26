package com.karasiq.shadowcloud.actors

import java.util.concurrent.TimeoutException

import scala.collection.mutable.{Set ⇒ MSet}
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.NotUsed
import akka.actor.{ActorLogging, DeadLetterSuppression, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.persistence._
import akka.stream.{ActorMaterializer, Materializer}
import akka.stream.scaladsl.{Flow, Sink, Source}

import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents._
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.storage.{Repository, StorageIOResult}
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexMerger, IndexRepositoryStreams}

object RegionIndex {
  case class ID(storageId: String, regionId: String)
  private type LocalKey = Long

  // Messages
  sealed trait Message
  case object GetIndex extends Message with MessageStatus[ID, IndexMerger.State[Long]]
  case class WriteDiff(diff: IndexDiff) extends Message
  object WriteDiff extends MessageStatus[IndexDiff, IndexDiff]
  case object Compact extends Message
  case object Synchronize extends Message

  // Internal messages
  private sealed trait InternalMessage extends Message with PossiblyHarmful
  private case class KeysLoaded(keys: Set[LocalKey]) extends InternalMessage
  private case class ReadSuccess(result: IndexIOResult[LocalKey]) extends InternalMessage
  private case class WriteSuccess(result: IndexIOResult[LocalKey]) extends InternalMessage
  private case class CompactSuccess(deleted: Set[LocalKey], result: IndexIOResult[LocalKey]) extends InternalMessage
  private case object StreamCompleted extends InternalMessage with DeadLetterSuppression

  // Snapshot
  private case class Snapshot(state: IndexMerger.State[Long])

  // Props
  def props(storageId: String, regionId: String, repository: Repository[Long]): Props = {
    Props(new RegionIndex(storageId, regionId, repository))
  }
}

private final class RegionIndex(storageId: String, regionId: String, repository: Repository[Long])
  extends PersistentActor with ActorLogging {
  import context.dispatcher

  import RegionIndex._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  require(storageId.nonEmpty && regionId.nonEmpty, "Invalid storage identifier")
  override val persistenceId: String = s"index_${storageId}_$regionId"

  private[this] implicit val materializer: Materializer = ActorMaterializer()
  private[this] val sc = ShadowCloud()
  private[this] val index = IndexMerger()
  private[this] val config = sc.storageConfig(storageId)
  private[this] val streams = IndexRepositoryStreams(config)
  private[this] var compactRequested = false

  // -----------------------------------------------------------------------
  // Local operations
  // -----------------------------------------------------------------------
  def receiveDefault: Receive = {
    case GetIndex ⇒
      deferAsync(()) { _ ⇒
        sender() ! GetIndex.Success(ID(storageId, regionId), IndexMerger.state(index))
      }

    case WriteDiff(diff) ⇒
      log.debug("Pending diff added: {}", diff)
      persistAsync(PendingIndexUpdated(regionId, diff)) { e ⇒
        updateState(e)
        sender() ! WriteDiff.Success(diff, index.pending)
      }

    case Compact ⇒
      if (!compactRequested) {
        log.debug("Index compaction requested")
        compactRequested = true
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
      deferAsync(NotUsed)(_ ⇒ syncStartRead())
  }

  // -----------------------------------------------------------------------
  // Default receive
  // -----------------------------------------------------------------------
  override def receiveRecover: Receive = {
    case SnapshotOffer(metadata, Snapshot(state)) ⇒
      log.debug("Loading snapshot: {}", metadata)
      updateState(IndexLoaded(regionId, state))

    case event: Event ⇒
      updateState(event)

    case RecoveryCompleted ⇒
      scheduleSync(10 seconds) // Initial sync
      context.setReceiveTimeout(config.syncInterval * 10)
  }

  override def receiveCommand: Receive = {
    receiveWait.orElse(receiveDefault)
  }

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  private[this] def updateState(event: Event): Unit = {
    sc.eventStreams.publishStorageEvent(storageId, event)
    event match {
      case IndexUpdated(`regionId`, sequenceNr, diff, _) ⇒
        index.add(sequenceNr, diff)

      case PendingIndexUpdated(`regionId`, diff) ⇒
        index.addPending(diff)

      case IndexLoaded(`regionId`, state) ⇒
        index.load(state)

      case IndexDeleted(`regionId`, sequenceNrs) ⇒
        index.delete(sequenceNrs)

      case _ ⇒
        log.warning("Event not handled: {}", event)
    }
  }

  // -----------------------------------------------------------------------
  // Synchronization
  // -----------------------------------------------------------------------
  private[this] def scheduleSync(duration: FiniteDuration = config.syncInterval): Unit = {
    if (log.isDebugEnabled) log.debug("Scheduling synchronize in {}", duration.toCoarsest)
    context.system.scheduler.scheduleOnce(duration, self, Synchronize)
    become(receiveWait)
  }

  private[this] def syncStartRead(): Unit = {
    def receivePreRead(loadedKeys: Set[LocalKey] = Set.empty): Receive = {
      case KeysLoaded(keys) ⇒
        become(receivePreRead(loadedKeys ++ keys))

      case Status.Failure(error) ⇒
        log.error(error, "Diffs load failed")
        throw error

      case StreamCompleted ⇒
        deferAsync(NotUsed)(_ ⇒ startRead(loadedKeys))
    }

    def startRead(keys: Set[LocalKey]): Unit = {
      def receiveRead: Receive = {
        case ReadSuccess(IndexIOResult(sequenceNr, data, ioResult)) ⇒
          require(data.region == regionId && data.sequenceNr == sequenceNr, "Diff region or sequenceNr not match")
          val diff = data.diff
          ioResult match {
            case StorageIOResult.Success(path, _) ⇒
              log.info("Remote diff #{} received from {}: {}", sequenceNr, path, diff)
              persistAsync(IndexUpdated(regionId, sequenceNr, diff, remote = true))(updateState)

            case StorageIOResult.Failure(path, error) ⇒
              log.error(error, "Diff #{} read failed from {}", sequenceNr, path)
              throw error
          }

        case Status.Failure(error) ⇒
          log.error(error, "Diff read failed")
          throw error

        case StreamCompleted ⇒
          log.debug("Synchronization read completed")
          deferAsync(NotUsed)(_ ⇒ syncStartWrite())
      }

      def becomeRead(keys: Set[LocalKey]): Unit = {
        val keySeq = keys.filter(_ > index.lastSequenceNr)
          .toVector
          .sorted
        if (keySeq.nonEmpty) log.debug("Reading diffs: {}", keySeq)
        Source(keySeq)
          .via(streams.read(repository))
          .map(ReadSuccess)
          .runWith(Sink.actorRef(self, StreamCompleted))
        become(receiveRead)
      }

      val deletedDiffs = index.diffs.keySet.diff(keys)
      if (deletedDiffs.nonEmpty) {
        log.warning("Diffs deleted: {}", deletedDiffs)
        persistAsync(IndexDeleted(regionId, deletedDiffs.toSet))(updateState)
      }

      deferAsync(Unit)(_ ⇒ becomeRead(keys))
    }

    loadRepositoryKeys()
    become(receivePreRead())
  }

  private[this] def syncStartWrite(): Unit = {
    def receivePreWrite(loadedKeys: Set[LocalKey] = Set.empty): Receive = {
      case KeysLoaded(keys) ⇒
        become(receivePreWrite(loadedKeys ++ keys))

      case StreamCompleted ⇒
        deferAsync(NotUsed)(_ ⇒ startWrite(loadedKeys))
    }

    def startWrite(keys: Set[LocalKey]): Unit = {
      def receiveWrite: Receive = {
        case WriteSuccess(IndexIOResult(sequenceNr, IndexData(_, _, diff), ioResult)) ⇒ ioResult match {
          case StorageIOResult.Success(path, _) ⇒
            log.debug("Diff #{} written to {}: {}", sequenceNr, path, diff)
            persistAsync(IndexUpdated(regionId, sequenceNr, diff, remote = false))(updateState)

          case StorageIOResult.Failure(path, error) ⇒
            log.error(error, "Diff #{} write error to {}: {}", sequenceNr, path, diff)
        }

        case Status.Failure(error) ⇒
          log.error(error, "Write error")
          scheduleSync()

        case StreamCompleted ⇒
          log.debug("Synchronization write completed")
          deferAsync(NotUsed)(_ ⇒ syncStartCompact())
      }

      def becomeWrite(newSequenceNr: LocalKey, diff: IndexDiff): Unit = {
        log.debug("Writing pending diff: {}", diff)

        Source.single((newSequenceNr, diff))
          .alsoTo(Sink.foreach { case (sequenceNr, diff) ⇒
            log.info("Writing diff #{}: {}", sequenceNr, diff)
          })
          .via(toIndexDataWithKey)
          .via(streams.write(repository))
          .map(WriteSuccess)
          .runWith(Sink.actorRef(self, StreamCompleted))

        become(receiveWrite)
      }

      val maxKey = math.max(index.lastSequenceNr, (if (keys.isEmpty) 0L else keys.max) + 1)
      if (index.pending.nonEmpty) {
        becomeWrite(maxKey, index.pending)
      } else {
        scheduleSync()
      }
    }

    loadRepositoryKeys()
    become(receivePreWrite())
  }

  private[this] def syncStartCompact(): Unit = {
    def receiveCompact: Receive = {
      case CompactSuccess(deletedDiffs, newDiff) ⇒
        log.info("Compact success: deleted = {}, new = {}", deletedDiffs, newDiff)
        val diff = newDiff.diff.diff
        if (diff.isEmpty) {
          persistAsync(IndexDeleted(regionId, deletedDiffs))(updateState)
        } else {
          persistAllAsync(List(
            IndexDeleted(regionId, deletedDiffs),
            IndexUpdated(regionId, newDiff.key, diff, remote = false)
          ))(updateState)
        }

      case Status.Failure(error) ⇒
        log.error(error, "Compact error")
        scheduleSync()

      case StreamCompleted ⇒
        log.debug("Indexes compaction completed")
        compactRequested = false
        deferAsync(NotUsed)(_ ⇒ createSnapshot(() ⇒ scheduleSync()))
    }

    def becomeCompact(): Unit = {
      def compactIndex(state: IndexMerger.State[LocalKey]): Source[CompactSuccess, NotUsed] = {
        val index = IndexMerger.restore(0L, state)
        log.debug("Compacting diffs: {}", index.diffs)
        val diffs = index.diffs.toVector
        val newDiff = index.mergedDiff
        val newSequenceNr = index.lastSequenceNr + 1
        log.debug("Writing compacted diff #{}: {}", newSequenceNr, newDiff)
        val writeResult = if (newDiff.isEmpty) {
          // Skip write
          val empty = IndexIOResult(newSequenceNr, IndexData.empty, StorageIOResult.Success("", 0))
          Source.single(empty)
        } else {
          Source.single(newSequenceNr → newDiff)
            .via(toIndexDataWithKey)
            .via(streams.write(repository))
            .log("compact-write")
        }

        writeResult.flatMapConcat(wr ⇒ wr.ioResult match {
          case StorageIOResult.Success(_, _) ⇒
            Source(diffs)
              .map(_._1)
              .via(streams.delete(repository))
              .log("compact-delete")
              .filter(_.ioResult.isSuccess)
              .map(_.key)
              .fold(Set.empty[Long])(_ + _)
              .map(CompactSuccess(_, wr))

          case StorageIOResult.Failure(_, error) ⇒
            Source.failed(error)
        })
      }

      Source.single(IndexMerger.state(index))
        .filter(_.diffs.length > 1)
        .flatMapConcat(compactIndex)
        .runWith(Sink.actorRef(self, StreamCompleted))

      become(receiveCompact)
    }

    if (compactRequested) {
      becomeCompact()
    } else {
      scheduleSync()
    }
  }

  // -----------------------------------------------------------------------
  // Snapshots
  // -----------------------------------------------------------------------
  private[this] def createSnapshot(after: () ⇒ Unit): Unit = {
    // TODO: Delete old messages
    saveSnapshot(Snapshot(IndexMerger.state(index)))
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
  private[this] def loadRepositoryKeys(): Unit = {
    repository.keys
      .fold(Set.empty[LocalKey])(_ + _)
      .map(KeysLoaded)
      .runWith(Sink.actorRef(self, StreamCompleted))
  }

  private[this] def toIndexDataWithKey = Flow[(LocalKey, IndexDiff)]
    .map { case (sequenceNr, diff) ⇒ (sequenceNr, IndexData(regionId, sequenceNr, diff)) }

  private[this] def become(receive: Receive): Unit = {
    context.become(receive.orElse(receiveDefault))
  }
}
