package com.karasiq.shadowcloud.actors

import akka.Done
import akka.actor.{Actor, ActorLogging, ActorRef, Terminated}
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.crypto.EncryptionModule
import com.karasiq.shadowcloud.index.{Chunk, ChunkIndex, ChunkIndexDiff}
import com.karasiq.shadowcloud.streams.ChunkVerifier

import scala.collection.mutable
import scala.language.postfixOps
import scala.util.Random
import scala.util.control.NonFatal

object ChunkDispatcher {
  private case class ChunkStatus(time: Long, chunk: Chunk, dispatchers: Set[ActorRef] = Set.empty, waiters: Set[ActorRef] = Set.empty)

  case class Register(index: ChunkIndex)
  case class Update(chunks: ChunkIndexDiff)
  case class WriteChunk(chunk: Chunk)
  object WriteChunk {
    sealed trait Status
    case class Success(chunk: Chunk) extends Status
    case class Failure(chunk: Chunk, error: Throwable) extends Status
  }
}

class ChunkDispatcher extends Actor with ActorLogging {
  import ChunkDispatcher._

  val dispatchers = mutable.Set[ActorRef]() // TODO: Quota control
  val chunks = mutable.AnyRefMap[ByteString, ChunkStatus]()
  val pending = mutable.AnyRefMap[ByteString, ChunkStatus]()

  def receive = {
    case WriteChunk(chunk) ⇒
      require(chunk.data.nonEmpty && chunk.checksum.hash.nonEmpty)
      chunks.get(chunk.checksum.hash) match {
        case Some(ChunkStatus(_, existing, _, _)) ⇒
          val encryptor = EncryptionModule(existing.encryption.method)
          val chunkWithData = existing.copy(data = chunk.data.copy(encrypted = encryptor.encrypt(chunk.data.plain, existing.encryption)))
          Source.single(chunkWithData)
            .via(new ChunkVerifier)
            .map[WriteChunk.Status](WriteChunk.Success)
            .recover { case NonFatal(exc) ⇒ WriteChunk.Failure(chunkWithData, exc) }
            .runWith(Sink.actorRef(sender(), Done))

        case None ⇒
          pending.get(chunk.checksum.hash) match {
            case Some(ChunkStatus(_, _, _, waiters)) ⇒
              waiters += sender()

            case None ⇒
              val written = writeChunk(chunk)
              pending += chunk.checksum.hash → ChunkStatus(System.currentTimeMillis(), chunk, written, Set(sender()))
          }
      }

    case WriteChunk.Success(chunk) ⇒
      require(chunk.data.nonEmpty && chunk.checksum.hash.nonEmpty)
      registerChunk(sender(), chunk)

    case WriteChunk.Failure(chunk, error) ⇒
      log.error(error, "Chunk write failed: {}", chunk)
      pending.get(chunk.checksum.hash) match {
        case Some(status @ ChunkStatus(_, existing, dispatchers, _)) if existing.withoutData == chunk.withoutData ⇒
          val written = writeChunk(chunk)
          pending += chunk.checksum.hash → status.copy(dispatchers = dispatchers - sender() ++ written)

        case _ ⇒
          // Pass
      }

    case Register(index) ⇒
      val dispatcher = sender()
      if (dispatchers.contains(dispatcher)) unregister(dispatcher)
      register(dispatcher, index)
      retryPending()

    case Update(diff) ⇒
      require(dispatchers.contains(sender()))
      update(sender(), diff)

    case Terminated(dispatcher) ⇒
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
      case Some(ChunkStatus(_, existing, _, waiters)) if chunk.withoutData == existing.withoutData ⇒
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
      case Some(status @ ChunkStatus(_, `chunk`, dispatchers, _)) ⇒
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
      case Some(status @ ChunkStatus(_, `chunk`, dispatchers, _)) ⇒
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
