package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, DeadLetterSuppression, PossiblyHarmful, Props, ReceiveTimeout, Status}
import akka.event.Logging
import akka.persistence._
import akka.stream.ActorAttributes
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.events.StorageEvents._
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperation}
import com.karasiq.shadowcloud.index.IndexData
import com.karasiq.shadowcloud.index.diffs.IndexDiff
import com.karasiq.shadowcloud.model.utils.SyncReport
import com.karasiq.shadowcloud.model.{RegionId, SequenceNr, StorageId}
import com.karasiq.shadowcloud.storage.StorageIOResult
import com.karasiq.shadowcloud.storage.props.StorageProps
import com.karasiq.shadowcloud.storage.repository.Repository
import com.karasiq.shadowcloud.storage.utils.{IndexIOResult, IndexMerger, IndexRepositoryStreams}
import com.karasiq.shadowcloud.utils.DiffStats

import scala.concurrent.duration._

object RegionIndex {
  // Types
  final case class RegionIndexId(storageId: StorageId, regionId: RegionId) {
    private[actors] def toPersistenceId: String = {
      s"index_${storageId}_$regionId"
    }
  }

  // Messages
  sealed trait Message
  case object GetIndex                        extends Message with MessageStatus[RegionIndexId, IndexMerger.State[SequenceNr]]
  final case class WriteDiff(diff: IndexDiff) extends Message
  object WriteDiff                            extends MessageStatus[IndexDiff, IndexDiff]
  case object Compact                         extends Message
  case object Synchronize                     extends Message with MessageStatus[RegionIndexId, SyncReport]
  case object DeleteHistory                   extends Message with PossiblyHarmful with MessageStatus[RegionIndexId, Done]

  // Internal messages
  private sealed trait InternalMessage                                                                          extends Message with PossiblyHarmful
  private final case class KeysLoaded(keys: Set[SequenceNr])                                                    extends InternalMessage
  private final case class ReadSuccess(result: IndexIOResult[SequenceNr])                                       extends InternalMessage
  private final case class WriteSuccess(result: IndexIOResult[SequenceNr])                                      extends InternalMessage
  private final case class CompactSuccess(deleted: Set[SequenceNr], created: Option[IndexIOResult[SequenceNr]]) extends InternalMessage
  private case object StreamCompleted                                                                           extends InternalMessage with DeadLetterSuppression

  // Snapshot
  private case class Snapshot(state: IndexMerger.State[SequenceNr])

  // Props
  def props(storageId: StorageId, regionId: RegionId, storageProps: StorageProps, repository: Repository[SequenceNr]): Props = {
    Props(new RegionIndex(storageId, regionId, storageProps, repository))
  }
}

private[actors] final class RegionIndex(storageId: StorageId, regionId: RegionId, storageProps: StorageProps, repository: Repository[SequenceNr])
    extends PersistentActor
    with ActorLogging {

  import RegionIndex._

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  require(storageId.nonEmpty && regionId.nonEmpty, "Invalid storage identifier")

  import context.dispatcher
  private[this] implicit lazy val sc = ShadowCloud()
  private[this] lazy val config      = sc.configs.storageConfig(storageId, storageProps)

  import sc.implicits.materializer

  private[this] object state {
    val indexId       = RegionIndexId(storageId, regionId)
    val persistenceId = indexId.toPersistenceId

    val index   = IndexMerger.sequential()
    val streams = IndexRepositoryStreams(regionId, config)

    var compactRequested = false
    var diffsSaved       = 0
    var diffStats        = DiffStats.empty

    val pendingSync       = new PendingOperation[RegionIndexId]
    var pendingSyncReport = SyncReport.empty

    def updateReport(f: SyncReport ⇒ SyncReport): Unit = {
      pendingSyncReport = f(pendingSyncReport)
    }
  }

  override def journalPluginId: String  = sc.config.persistence.journalPlugin
  override def snapshotPluginId: String = sc.config.persistence.snapshotPlugin
  override def persistenceId: String    = state.persistenceId

  // -----------------------------------------------------------------------
  // Local operations
  // -----------------------------------------------------------------------
  def receiveDefault: Receive = {
    case GetIndex ⇒
      deferAsync(()) { _ ⇒
        sender() ! GetIndex.Success(RegionIndexId(storageId, regionId), IndexMerger.createState(state.index))
      }

    case WriteDiff(diff) ⇒
      log.debug("Pending diff added: {}", diff)
      persistAsync(PendingIndexUpdated(regionId, diff)) { event ⇒
        updateState(event)
        sender() ! WriteDiff.Success(diff, state.index.pending)
      }

    case Compact ⇒
      if (config.immutable) {
        log.warning("Storage is immutable, unable to compact indexes")
      } else if (!state.compactRequested) {
        log.debug("Index compaction requested")
        state.compactRequested = true
      }

    case Synchronize ⇒
      if (sender() != self && sender() != Actor.noSender)
        state.pendingSync.addWaiter(state.indexId, sender())

    case DeleteHistory ⇒
      val sender = context.sender()
      deleteSnapshots(SnapshotSelectionCriteria())
      deleteMessages(Long.MaxValue)
      context.become {
        case result @ (DeleteSnapshotsSuccess(_) | DeleteSnapshotsFailure(_, _)) ⇒
          log.warning("Snapshots cleared: {}", result)

        case result @ (DeleteMessagesSuccess(_) | DeleteMessagesFailure(_, _)) ⇒
          log.warning("Local history cleared: {}", result)
          sender ! DeleteHistory.Success(state.indexId, Done)
          context.stop(self)
      }
  }

  // -----------------------------------------------------------------------
  // Idle state
  // -----------------------------------------------------------------------
  def receiveWait: Receive = {
    case Synchronize ⇒
      log.debug("Starting synchronization")

      if (sender() != self && sender() != Actor.noSender)
        state.pendingSync.addWaiter(state.indexId, sender())

      deferAsync(NotUsed)(_ ⇒ synchronization.start())
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
      // Initial sync
      log.debug("Region index recovery completed")
      synchronization.scheduleNext( /* 10 seconds */ )
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
      case PendingIndexUpdated(`regionId`, diff) ⇒
        state.index.addPending(diff)
        state.diffsSaved += 1

      case IndexUpdated(`regionId`, sequenceNr, diff, _) ⇒
        state.index.add(sequenceNr, diff)
        state.diffStats += diff
        state.diffsSaved += 1

      case IndexDeleted(`regionId`, sequenceNrs) ⇒
        state.index.delete(sequenceNrs)
        state.diffStats = DiffStats.fromIndex(state.index)

      case IndexLoaded(`regionId`, snapshot) ⇒
        state.index.load(snapshot)
        state.diffsSaved = 0
        state.diffStats = DiffStats.fromIndex(state.index)

      case _ ⇒
        log.warning("Event not handled: {}", event)
    }
  }

  // -----------------------------------------------------------------------
  // Synchronization
  // -----------------------------------------------------------------------
  private[this] object synchronization {
    def start(): Unit = {
      becomeRead() // Read -> (opt) write -> (opt) compact
    }

    def finish(): Unit = {
      if (state.pendingSyncReport.nonEmpty) log.info("Synchronization finished: {}", state.pendingSyncReport)
      state.pendingSync.finish(state.indexId, Synchronize.Success(state.indexId, state.pendingSyncReport))
      state.pendingSyncReport = SyncReport.empty
    }

    def scheduleNext(duration: FiniteDuration = config.index.syncInterval): Unit = {
      this.finish()
      if (log.isDebugEnabled) log.debug("Scheduling synchronize in {}", duration.toCoarsest)
      context.system.scheduler.scheduleOnce(duration, self, Synchronize)
      becomeOrDefault(receiveWait)
    }

    private[this] def becomeRead(): Unit = {
      def receivePreRead(loadedKeys: Set[SequenceNr] = Set.empty): Receive = {
        case KeysLoaded(keys) ⇒
          becomeOrDefault(receivePreRead(loadedKeys ++ keys))

        case Status.Failure(error) ⇒
          log.debug("Diffs load failed: {}", error)
          // throw error
          synchronization.scheduleNext()

        case StreamCompleted ⇒
          deferAsync(NotUsed)(_ ⇒ startRead(loadedKeys))
      }

      def startRead(storageKeys: Set[SequenceNr]): Unit = {
        def receiveRead: Receive = {
          case ReadSuccess(indexIOResult @ IndexIOResult(sequenceNr, data, ioResult)) ⇒
            if (data.regionId == regionId && data.sequenceNr == sequenceNr) {
              val diff = data.diff
              ioResult match {
                case StorageIOResult.Success(path, _) ⇒
                  log.info("Remote diff #{} received from {}: {}", sequenceNr, path, diff)
                  state.updateReport(r ⇒ r.copy(read = r.read + (sequenceNr → diff)))
                  persistAsync(IndexUpdated(regionId, sequenceNr, diff, remote = true))(updateState)

                case StorageIOResult.Failure(path, error) ⇒
                  log.error(error, "Diff #{} read failed from {}", sequenceNr, path)
                // throw error
              }
            } else {
              log.warning("Diff region or sequenceNr not match: {}", indexIOResult)
            }

          case Status.Failure(error) ⇒
            log.error(error, "Diff read failed")
            // throw error
            synchronization.scheduleNext()

          case StreamCompleted ⇒
            log.debug("Synchronization read completed")
            deferAsync(NotUsed)(_ ⇒ becomeWrite())
        }

        def becomeRead(keys: Set[SequenceNr]): Unit = {
          val existingKeys = state.index.diffs.keySet
          val keySeq       = keys.diff(existingKeys).toVector.sorted

          if (keySeq.nonEmpty) log.debug("Reading diffs: {}", keySeq)
          Source(keySeq)
            .via(state.streams.read(repository))
            .map(ReadSuccess)
            .idleTimeout(sc.config.timeouts.indexRead)
            .runWith(Sink.actorRef(self, StreamCompleted))

          becomeOrDefault(receiveRead)
        }

        val deletedDiffs = state.index.diffs.keySet.diff(storageKeys)
        if (deletedDiffs.nonEmpty && !config.index.keepDeleted) {
          log.warning("Diffs deleted: {}", deletedDiffs)
          state.pendingSyncReport = state.pendingSyncReport
            .copy(deleted = state.pendingSyncReport.deleted ++ deletedDiffs)
          persistAsync(IndexDeleted(regionId, deletedDiffs.toSet))(updateState)
        }

        deferAsync(NotUsed)(_ ⇒ becomeRead(storageKeys))
      }

      loadRepositoryKeys()
      becomeOrDefault(receivePreRead())
    }

    private[this] def becomeWrite(): Unit = {
      def receivePreWrite(loadedKeys: Set[SequenceNr] = Set.empty): Receive = {
        case KeysLoaded(keys) ⇒
          becomeOrDefault(receivePreWrite(loadedKeys ++ keys))

        case Status.Failure(error) ⇒
          log.debug("Keys load error: {}", error)
          synchronization.scheduleNext()

        case StreamCompleted ⇒
          deferAsync(NotUsed)(_ ⇒ startWrite(loadedKeys))
      }

      def startWrite(keys: Set[SequenceNr]): Unit = {
        def receiveWrite: Receive = {
          case WriteSuccess(IndexIOResult(sequenceNr, IndexData(_, _, diff), ioResult)) ⇒
            ioResult match {
              case StorageIOResult.Success(path, _) ⇒
                log.debug("Diff #{} written to {}: {}", sequenceNr, path, diff)
                state.updateReport(r ⇒ r.copy(written = r.written + (sequenceNr → diff)))
                persistAsync(IndexUpdated(regionId, sequenceNr, diff, remote = false))(updateState)

              case StorageIOResult.Failure(path, error) ⇒
                log.error(error, "Diff #{} write error to {}: {}", sequenceNr, path, diff)
            }

          case Status.Failure(error) ⇒
            log.error(error, "Write error")
            synchronization.scheduleNext()

          case StreamCompleted ⇒
            log.debug("Synchronization write completed")
            if (state.index.pending.nonEmpty) log.warning("Pending index is not flushed: {}", state.index.pending)
            deferAsync(NotUsed)(_ ⇒ becomeCompactOrSnapshotOrFinish())
        }

        def becomeWrite(newSequenceNr: SequenceNr, diff: IndexDiff): Unit = {
          log.debug("Writing pending diff: {}", diff)

          Source
            .single((newSequenceNr, diff))
            .alsoTo(Sink.foreach {
              case (sequenceNr, diff) ⇒
                log.info("Writing diff #{}: {}", sequenceNr, diff)
            })
            .via(toIndexDataWithKey)
            .via(state.streams.write(repository))
            .map(WriteSuccess)
            .completionTimeout(sc.config.timeouts.indexWrite)
            .runWith(Sink.actorRef(self, StreamCompleted))

          becomeOrDefault(receiveWrite)
        }

        val maxKey = math.max(state.index.lastSequenceNr, if (keys.isEmpty) 0L else keys.max)
        if (state.index.pending.nonEmpty) {
          becomeWrite(maxKey + 1, state.index.pending)
        } else {
          becomeCompactOrSnapshotOrFinish()
        }
      }

      loadRepositoryKeys()
      becomeOrDefault(receivePreWrite())
    }

    private[this] def becomeCompact(): Unit = {
      def receiveCompact: Receive = {
        case CompactSuccess(deletedDiffs, newDiff) ⇒
          log.info("Compact success: deleted = {}, new = {}", deletedDiffs, newDiff)
          state.updateReport(r ⇒ r.copy(deleted = r.deleted ++ deletedDiffs))
          newDiff match {
            case Some(IndexIOResult(sequenceNr, IndexData(_, _, diff), _)) if diff.nonEmpty ⇒
              state.updateReport(r ⇒ r.copy(written = r.written + (sequenceNr → diff)))
              persistAllAsync(
                List(
                  IndexDeleted(regionId, deletedDiffs),
                  IndexUpdated(regionId, sequenceNr, diff, remote = false)
                )
              )(updateState)

            case None ⇒
              persistAsync(IndexDeleted(regionId, deletedDiffs))(updateState)
          }

        case Status.Failure(error) ⇒
          log.error(error, "Compact error")
          synchronization.scheduleNext()

        case StreamCompleted ⇒
          log.debug("Indexes compaction completed")
          state.compactRequested = false
          deferAsync(NotUsed)(_ ⇒ createSnapshot(() ⇒ synchronization.scheduleNext()))
      }

      def compactIndex(indexState: IndexMerger.State[SequenceNr]): Source[CompactSuccess, NotUsed] = {
        val index = IndexMerger.restore(indexState)
        log.debug("Compacting diffs: {}", index.diffs)

        val diffsToDelete =
          if (config.index.compactDeleteOld) index.diffs.keys.toVector
          else Vector.empty

        val newDiff       = IndexMerger.compact(index)
        val newSequenceNr = index.lastSequenceNr + 1

        log.debug("Writing compacted diff #{}: {}", newSequenceNr, newDiff)
        val writeResult = if (newDiff.isEmpty) {
          // Skip write
          val empty = IndexIOResult(newSequenceNr, IndexData.empty, StorageIOResult.empty)
          Source.single(empty)
        } else {
          Source
            .single(newSequenceNr → newDiff)
            .via(toIndexDataWithKey)
            .via(state.streams.write(repository))
            .log("compact-write")
        }

        writeResult.flatMapConcat(
          wr ⇒
            wr.ioResult match {
              case StorageIOResult.Success(_, _) ⇒
                Source(diffsToDelete)
                  .via(state.streams.delete(repository))
                  .log("compact-delete")
                  .withAttributes(ActorAttributes.logLevels(onElement = Logging.InfoLevel))
                  .filter(_.ioResult.isSuccess)
                  .map(_.key)
                  .fold(Set.empty[SequenceNr])(_ + _)
                  .map(CompactSuccess(_, Some(wr).filterNot(_.diff.isEmpty)))

              case StorageIOResult.Failure(_, error) ⇒
                Source.failed(error)
            }
        )
      }

      Source
        .single(IndexMerger.createState(state.index))
        .filter(_.diffs.length > 1)
        .flatMapConcat(compactIndex)
        .runWith(Sink.actorRef(self, StreamCompleted))

      becomeOrDefault(receiveCompact)
    }

    private[this] def becomeCompactOrSnapshotOrFinish(): Unit = {
      if (isCompactRequired()) {
        becomeCompact()
      } else if (isSnapshotRequired()) {
        createSnapshot(() ⇒ synchronization.scheduleNext())
      } else {
        synchronization.scheduleNext()
      }
    }
  }

  // -----------------------------------------------------------------------
  // Snapshots
  // -----------------------------------------------------------------------
  private[this] def createSnapshot(after: () ⇒ Unit): Unit = {
    val snapshot = Snapshot(IndexMerger.createState(state.index))
    log.info("Saving region index snapshot: {}", snapshot)
    deleteSnapshot(this.snapshotSequenceNr)
    saveSnapshot(snapshot)

    becomeOrDefault {
      case SaveSnapshotSuccess(snapshot) ⇒
        log.info("Snapshot saved: {}", snapshot)
        state.diffsSaved = 0

        if (config.index.snapshotClearHistory) {
          deleteSnapshots(SnapshotSelectionCriteria(maxSequenceNr = snapshot.sequenceNr - 1))
          deleteMessages(snapshot.sequenceNr)

          val timeout = context.system.scheduler.scheduleOnce(15 seconds, self, ReceiveTimeout)
          becomeOrDefault {
            case r @ (DeleteMessagesSuccess(_) | DeleteMessagesFailure(_, _)) ⇒
              log.debug("Delete old history result: {}", r)
              timeout.cancel()
              after()

            case ReceiveTimeout ⇒
              log.error("Timeout deleting messages")
              context.stop(self)
          }
        } else after()

      case SaveSnapshotFailure(snapshot, error) ⇒
        log.error(error, "Snapshot save failed: {}", snapshot)
        after()
    }
  }

  override def postStop(): Unit = {
    state.pendingSync.finishAll(indexId ⇒ Synchronize.Failure(indexId, new RuntimeException("Index dispatcher stopped")))
    super.postStop()
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  private[this] def isSnapshotRequired(): Boolean = {
    config.index.snapshotThreshold > 0 && state.diffsSaved > config.index.snapshotThreshold
  }

  private[this] def isCompactRequired(): Boolean = {
    !config.immutable &&
    (state.compactRequested || (config.index.compactThreshold > 0 && state.diffStats.deletes > config.index.compactThreshold))
  }

  private[this] def loadRepositoryKeys(): Unit = {
    repository.keys
      .fold(Set.empty[SequenceNr])(_ + _)
      // .log("index-keys")
      .map(KeysLoaded)
      .completionTimeout(sc.config.timeouts.indexList)
      .runWith(Sink.actorRef(self, StreamCompleted))
  }

  private[this] def toIndexDataWithKey =
    Flow[(SequenceNr, IndexDiff)]
      .map { case (sequenceNr, diff) ⇒ (sequenceNr, IndexData(regionId, sequenceNr, diff)) }

  private[this] def becomeOrDefault(receive: Receive): Unit = {
    context.become(receive.orElse(receiveDefault))
  }

  override def preRestart(reason: Throwable, message: Option[Any]): Unit = {
    reason.printStackTrace()
    super.preRestart(reason, message)
  }
}
