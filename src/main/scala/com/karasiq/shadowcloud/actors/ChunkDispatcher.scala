package com.karasiq.shadowcloud.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.karasiq.shadowcloud.actors.StorageDispatcher.{ReadChunk, WriteChunk}
import com.karasiq.shadowcloud.crypto.EncryptionModule
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndex, ChunkIndexDiff}
import com.karasiq.shadowcloud.streams.ChunkVerifier
import com.karasiq.shadowcloud.utils.Utils

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.Random
import scala.util.control.NonFatal

object ChunkDispatcher {
  private[actors] case class ChunkStatus(time: Long, chunk: Chunk, dispatchers: Set[ActorRef] = Set.empty, waiters: Set[ActorRef] = Set.empty)

  case class Register(index: ChunkIndex)
  case class Update(chunks: ChunkIndexDiff)
}

class ChunkDispatcher extends Actor with ActorLogging {
  import ChunkDispatcher._
  implicit val actorMaterializer = ActorMaterializer()
  val dispatchers = mutable.Set[ActorRef]() // TODO: Quota control
  val chunks = mutable.AnyRefMap[ByteString, ChunkStatus]()
  val pending = mutable.AnyRefMap[ByteString, ChunkStatus]()

  def receive = {
    case msg @ ReadChunk(chunk) ⇒
      chunks.get(chunk.checksum.hash) match {
        case Some(ChunkStatus(_, existing, dispatchers, _)) if Utils.isSameChunk(existing, chunk) ⇒
          Random.shuffle(dispatchers).headOption match {
            case Some(dispatcher) ⇒
              log.debug("Reading chunk from {}: {}", dispatcher, chunk)
              dispatcher.forward(msg)

            case None ⇒
              log.debug("Chunk unavailable: {}", chunk)
              sender() ! ReadChunk.Failure(chunk, new IllegalArgumentException("Chunk unavailable"))
          }

        case _ ⇒
          pending.get(chunk.checksum.hash) match {
            case Some(ChunkStatus(_, chunkWithData, _, _)) if Utils.isSameChunk(chunkWithData, chunk) ⇒
              log.debug("Chunk extracted from pending: {}", chunkWithData)
              sender() ! ReadChunk.Success(chunkWithData)

            case _ ⇒
              log.debug("Chunk not found: {}", chunk)
              sender() ! ReadChunk.Failure(chunk, new IllegalArgumentException("Chunk not found"))
          }
      }

    case WriteChunk(chunk) ⇒
      require(chunk.data.nonEmpty && chunk.checksum.hash.nonEmpty)
      val sender = context.sender()
      chunks.get(chunk.checksum.hash) match {
        case Some(ChunkStatus(_, existing, _, _)) ⇒ // Ignore different encryption
          val encryptor = EncryptionModule(existing.encryption.method)
          val chunkWithData = existing.copy(data = chunk.data.copy(encrypted = encryptor.encrypt(chunk.data.plain, existing.encryption)))
          log.debug("Chunk restored from index, write skipped: {}", chunkWithData)
          Source.single(chunkWithData)
            .via(new ChunkVerifier)
            .map(WriteChunk.Success)
            .recover { case NonFatal(exc) ⇒ WriteChunk.Failure(chunkWithData, exc) }
            .runForeach(sender ! _)

        case None ⇒
          pending.get(chunk.checksum.hash) match {
            case Some(status @ ChunkStatus(_, _, _, waiters)) ⇒
              log.debug("Already writing chunk, adding to queue: {}", chunk)
              pending += chunk.checksum.hash → status.copy(waiters = waiters + sender)

            case None ⇒
              val written = writeChunk(chunk)
              log.debug("Writing chunk to {}: {}", written, chunk)
              pending += chunk.checksum.hash → ChunkStatus(System.currentTimeMillis(), chunk, written, Set(sender))
          }
      }

    case WriteChunk.Success(chunk) ⇒
      require(chunk.data.nonEmpty && chunk.checksum.hash.nonEmpty)
      log.debug("Chunk write success: {}", chunk)
      registerChunk(sender(), chunk)

    case WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failed: {}", chunk)
      pending.get(chunk.checksum.hash) match {
        case Some(status @ ChunkStatus(_, existing, dispatchers, _)) if Utils.isSameChunk(existing, chunk) ⇒
          val written = writeChunk(chunk)
          pending += chunk.checksum.hash → status.copy(dispatchers = dispatchers - sender() ++ written)

        case _ ⇒
          // Pass
      }

    case Register(index) ⇒
      val dispatcher = sender()
      if (log.isDebugEnabled) log.debug("Registered storage {} with {} entries", dispatcher, index.chunks.size)
      if (dispatchers.contains(dispatcher)) unregister(dispatcher)
      register(dispatcher, index)
      retryPending()

    case Update(diff) ⇒
      val dispatcher = sender()
      if (log.isDebugEnabled) log.debug("Storage index updated {}: {}", dispatcher, diff)
      require(dispatchers.contains(dispatcher))
      update(dispatcher, diff)

    case Terminated(dispatcher) ⇒
      log.debug("Storage dispatcher terminated: {}", dispatcher)
      unregister(dispatcher)
  }

  def writeChunk(chunk: Chunk): Set[ActorRef] = {
    require(chunk.data.nonEmpty)
    val selected = Random.shuffle(dispatchers).take(1) // TODO: Replication
    selected.foreach(_ ! WriteChunk(chunk))
    selected.toSet
  }

  def retryPending(): Unit = {
    pending.foreach {
      case (hash, status @ ChunkStatus(_, chunk, dispatchers, _)) ⇒
        if (dispatchers.isEmpty)
          pending += hash → status.copy(dispatchers = writeChunk(chunk))
    }
  }

  def register(dispatcher: ActorRef, index: ChunkIndex): Unit = {
    context.watch(dispatcher)
    dispatchers += dispatcher
    index.chunks.foreach(registerChunk(dispatcher, _))
  }

  def resolvePending(chunk: Chunk): Unit = {
    pending.get(chunk.checksum.hash) match {
      case Some(ChunkStatus(_, existing, _, waiters)) if Utils.isSameChunk(existing, chunk) ⇒
        log.debug("Resolved pending chunk: {}", chunk)
        require(existing.data.nonEmpty)
        waiters.foreach(_ ! WriteChunk.Success(existing))
        pending -= chunk.checksum.hash

      case Some(ChunkStatus(_, existing, _, _)) ⇒
        log.error("Chunk conflict: {} / {}", existing, chunk)

      case None ⇒
        // Pass
    }
  }

  def registerChunk(dispatcher: ActorRef, chunk: Chunk): Unit = {
    val hash: ByteString = chunk.checksum.hash
    chunks.get(hash) match {
      case Some(status @ ChunkStatus(_, existing, dispatchers, _)) if Utils.isSameChunk(existing, chunk) ⇒
        chunks += hash → status.copy(dispatchers = dispatchers + dispatcher)
        resolvePending(chunk)

      case Some(ChunkStatus(_, existing, _, _)) ⇒
        log.error("Chunk conflict: {} / {}", existing, chunk)

      case None ⇒
        chunks += hash → ChunkStatus(System.currentTimeMillis(), chunk.withoutData, Set(dispatcher))
        resolvePending(chunk)
    }
  }

  def unregister(dispatcher: ActorRef): Unit = {
    context.unwatch(dispatcher)
    dispatchers -= dispatcher
    pending.foreach {
      case (hash, status) ⇒
        pending += hash → status.copy(dispatchers = status.dispatchers - dispatcher)
    }
    chunks.foreach {
      case (hash, status) ⇒
        val newStatus = status.copy(dispatchers = status.dispatchers - dispatcher)
        if (newStatus.dispatchers.isEmpty) {
          log.warning("Chunk is lost: {}", newStatus.chunk)
          chunks -= hash
        } else {
          chunks += hash → newStatus
        }
    }
  }

  def unregisterChunk(dispatcher: ActorRef, chunk: Chunk): Unit = {
    val hash = chunk.checksum.hash
    chunks.get(hash) match {
      case Some(status @ ChunkStatus(_, existing, dispatchers, _)) if Utils.isSameChunk(existing, chunk) ⇒
        chunks += hash → status.copy(dispatchers = dispatchers + dispatcher)

      case Some(ChunkStatus(_, existing, _, _)) ⇒
        log.warning("Unknown chunk deleted: {} (existing: {})", chunk, existing)

      case None ⇒
        // Pass
    }
  }

  def update(dispatcher: ActorRef, diff: ChunkIndexDiff): Unit = {
    diff.deletedChunks.foreach(unregisterChunk(dispatcher, _))
    diff.newChunks.foreach(registerChunk(dispatcher, _))
  }
}
