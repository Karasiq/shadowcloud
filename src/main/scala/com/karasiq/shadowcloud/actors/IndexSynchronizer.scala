package com.karasiq.shadowcloud.actors

import akka.actor.{ActorLogging, ActorRef, Props, ReceiveTimeout, Status}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.karasiq.shadowcloud.index.IndexDiff
import com.karasiq.shadowcloud.storage.{BaseIndexRepository, IndexMerger, IndexRepository, IndexRepositoryStreams}
import com.karasiq.shadowcloud.utils.Utils

import scala.concurrent.duration._
import scala.language.postfixOps

object IndexSynchronizer {
  // Messages
  sealed trait Message
  case object Get extends Message
  case class Append(diff: IndexDiff) extends Message
  case object Synchronize extends Message

  // Events
  sealed trait Event
  case class Loaded(diffs: Seq[(Long, IndexDiff)]) extends Event
  case class PendingUpdate(diff: IndexDiff) extends Event
  case class Update(sequenceNr: Long, diff: IndexDiff, remote: Boolean) extends Event

  // Internal messages
  private case class ReadSuccess(sequenceNr: Long, indexDiff: IndexDiff) extends Message
  private case object CompleteRead extends Message
  private case class WriteSuccess(sequenceNr: Long, diff: IndexDiff) extends Message
  private case object CompleteWrite extends Message

  // Snapshot
  case class Snapshot(diffs: Seq[(Long, IndexDiff)])

  // Props
  def props(indexId: String, indexDispatcher: ActorRef, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams = IndexRepositoryStreams.default): Props = {
    Props(classOf[IndexSynchronizer], indexId, indexDispatcher, baseIndexRepository, streams)
  }
}

class IndexSynchronizer(indexId: String, indexDispatcher: ActorRef, baseIndexRepository: BaseIndexRepository, streams: IndexRepositoryStreams) extends PersistentActor with ActorLogging {
  import IndexSynchronizer._
  import context.dispatcher
  implicit val actorMaterializer = ActorMaterializer()
  val indexRepository = IndexRepository.incremental(baseIndexRepository)
  val merger = IndexMerger()
  val syncInterval = Utils.scalaDuration(Utils.config.getDuration("shadowcloud.index.sync-interval"))

  def persistenceId: String = s"index-$indexId"

  def updateState(event: Event): Unit = event match {
    case Update(sequenceNr, diff, _) ⇒
      indexDispatcher ! event
      merger.add(sequenceNr, diff)

    case PendingUpdate(diff) ⇒
      merger.addPending(diff)

    case Loaded(diffs) ⇒
      merger.clear()
      for ((sequenceNr, diff) ← diffs) {
        merger.add(sequenceNr, diff)
      }
      indexDispatcher ! Loaded(diffs)
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
      sender() ! Loaded(merger.diffs.toVector)

    case Append(diff) ⇒
      persistAsync(PendingUpdate(diff))(updateState)
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
      persistAsync(Update(sequenceNr, diff, remote = true))(updateState)

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
      persistAsync(Update(sequenceNr, diff, remote = false))(updateState)

    case Status.Failure(error) ⇒
      log.error(error, "Write error")
      scheduleSync()

    case CompleteWrite ⇒
      scheduleSync()
  }

  def receiveRecover: Receive = {
    case SnapshotOffer(_, Snapshot(diffs)) ⇒
      log.debug("Loading snapshot with sequence number: {}", diffs.lastOption.fold(0L)(_._1))
      updateState(Loaded(diffs))

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
