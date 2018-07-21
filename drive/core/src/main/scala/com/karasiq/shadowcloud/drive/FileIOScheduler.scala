package com.karasiq.shadowcloud.drive

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.{implicitConversions, postfixOps}

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, PoisonPill, Props, ReceiveTimeout}
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperation}
import com.karasiq.shadowcloud.drive.utils.ChunkPatchList._
import com.karasiq.shadowcloud.drive.FileIOScheduler._
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.drive.utils.{ChunkPatch, PendingChunkIO, WritesOptimizeStage}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.crypto.HashingMethod
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges
import com.karasiq.shadowcloud.streams.utils.ByteStreams

object FileIOScheduler {
  // -----------------------------------------------------------------------
  // Messages
  // -----------------------------------------------------------------------
  sealed trait Message
  final case class ReadData(range: ChunkRanges.Range) extends Message
  object ReadData extends MessageStatus[ChunkRanges.Range, ByteString]

  final case class WriteData(offset: Long, data: ByteString) extends Message {
    override def toString: String = s"WriteData($offset, ${data.length} bytes)"
  }
  object WriteData extends MessageStatus[WriteData, Done] {
    implicit def toChunkPatch(wd: WriteData): ChunkPatch = ChunkPatch(wd.offset, wd.data)
  }

  final case class CutFile(size: Long) extends Message
  object CutFile extends MessageStatus[File, Long]

  final case object Flush extends Message with MessageStatus[File, FlushResult]
  final case object PersistRevision extends Message with MessageStatus[File, File]
  final case object GetCurrentRevision extends Message with MessageStatus[File, File]
  final case object ReleaseFile extends Message with MessageStatus[File, File]

  // -----------------------------------------------------------------------
  // Internal Messages
  // -----------------------------------------------------------------------
  private sealed trait InternalMessage extends Message
  private final case class DropChunks(size: Long) extends InternalMessage
  private object DropChunks extends MessageStatus[File, Long]

  private final case class RevisionUpdated(newFile: File) extends InternalMessage

  // -----------------------------------------------------------------------
  // Misc
  // -----------------------------------------------------------------------
  sealed trait ChunkIOOperation {
    def range: ChunkRanges.Range
    def chunk: Chunk
  }
  object ChunkIOOperation {
    final case class ChunkRewritten(range: ChunkRanges.Range, oldChunk: Chunk, chunk: Chunk) extends ChunkIOOperation
    final case class ChunkAppended(range: ChunkRanges.Range, chunk: Chunk) extends ChunkIOOperation
  }

  final case class FlushResult(writes: Seq[ChunkPatch], ops: Seq[ChunkIOOperation])

  // -----------------------------------------------------------------------
  // Props
  // -----------------------------------------------------------------------
  def props(config: SCDriveConfig, regionId: RegionId, file: File): Props = {
    Props(new FileIOScheduler(config, regionId, file))
  }
}

class FileIOScheduler(config: SCDriveConfig, regionId: RegionId, file: File) extends Actor with ActorLogging {
  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  import context.dispatcher
  private[this] implicit val materializer = ActorMaterializer()
  private[this] val sc = ShadowCloud()
  import sc.implicits.defaultTimeout

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  object actorState {
    val pendingFlush = PendingOperation[NotUsed]
    var lastFlush = 0L

    def finishFlush(result: Flush.Status): Unit = {
      pendingFlush.finish(NotUsed, result)
      lastFlush = System.nanoTime()
    }
  }

  object dataState {
    var currentChunks = mutable.TreeMap(ChunkRanges.RangeList.zipWithRange(file.chunks).map(_.swap):_*)
    var pendingWrites = Seq.empty[ChunkPatch]
    var lastRevision = file
    var lastSetSize = file.checksum.size

    def isFlushRequired: Boolean = {
      (System.nanoTime() - actorState.lastFlush).nanos > config.fileIO.flushInterval ||
        pendingWrites.map(_.range.size).sum >= config.fileIO.flushLimit
    }

    def isChunksModified: Boolean = {
      pendingWrites.nonEmpty || isRevisionModified
    }

    def isRevisionModified: Boolean = {
      currentChunks.values.toSeq != lastRevision.chunks
    }

    def buildNewRevision(): File = {
      val newChunks = currentChunks.values.toVector
      val newSize = newChunks.map(_.checksum.size).sum
      val newEncSize = newChunks.map(_.checksum.encSize).sum
      val newChecksum = Checksum(HashingMethod.none, HashingMethod.none, newSize, ByteString.empty, newEncSize, ByteString.empty)
      File.modified(lastRevision, newChecksum, newChunks)
    }

    def fixRevisionBounds(): File = {
      val maxChunkEnd = if (currentChunks.isEmpty) 0 else currentChunks.keys.maxBy(_.end).end
      val maxPatchEnd = if (pendingWrites.isEmpty) 0 else pendingWrites.maxBy(_.range.end).range.end

      val newSize = math.max(lastSetSize, math.max(maxChunkEnd, maxPatchEnd))
      val newChecksum = Checksum(HashingMethod.none, HashingMethod.none, newSize, ByteString.empty, newSize, ByteString.empty)
      lastRevision.copy(checksum = newChecksum, chunks = Nil)
    }
  }

  // -----------------------------------------------------------------------
  // Data IO
  // -----------------------------------------------------------------------
  object dataIO {
    // -----------------------------------------------------------------------
    // Read
    // -----------------------------------------------------------------------
    def readStream(range: ChunkRanges.Range): Source[ByteString, NotUsed] = {
      val currentWrites = dataState.pendingWrites
      val patches = currentWrites.filter(wr ⇒ range.contains(wr.range))

      def chunksStream = {
        sc.streams.file.readChunkStreamRanged(regionId, dataState.currentChunks.values.toVector, range)
          .statefulMapConcat { () ⇒
            var position = range.start
            bytes ⇒ {
              val range = ChunkRanges.Range(position, position + bytes.length)
              val relatedPatches = patches.filter(wr ⇒ range.contains(wr.range))
              val patched = relatedPatches.patchChunk(range, bytes)

              position += bytes.length
              patched +: Nil
            }
          }
          .named("scDriveReadChunks")
      }

      def appendsStream = {
        val chunksEnd = dataState.currentChunks.lastOption.fold(0L)(_._1.end)
        if (currentWrites.forall(_.range.end <= chunksEnd)) {
          Source.empty[ByteString]
        } else {
          val fileEnd = math.max(chunksEnd, math.min(range.end, currentWrites.map(_.range.end).max))
          val appendsRange = ChunkRanges.Range(chunksEnd, fileEnd)
          Source
            .fromIterator(() ⇒ dataUtils.pendingAppends(currentWrites, chunksEnd))
            .map { case (range, chunk) ⇒
              val selectedRange = range.relativeTo(appendsRange)
              selectedRange.slice(chunk.data.plain)
            }
            // .via(ByteStreams.limit(appendsRange.size))
            .named("scDriveReadAppends")
        }
      }

      chunksStream.concat(appendsStream).named("scDriveRead")
    }

    // -----------------------------------------------------------------------
    // Write
    // -----------------------------------------------------------------------
    def createWritesStream(pendingWrites: Seq[ChunkPatch]): Source[PendingChunkIO, NotUsed] = {
      def rewrites = {
        val patchesByChunk = (for {
          write ← pendingWrites
          (range, chunk) ← dataState.currentChunks if range.contains(write.range)
        } yield ((chunk, range), write)).groupBy(_._1).mapValues(_.map(_._2))

        val sortedPatches = patchesByChunk
          .map { case ((chunk, range), patches) ⇒ PendingChunkIO.Rewrite(range, chunk, patches) }
          .toVector
          .sortBy(_.range.start)

        Source(sortedPatches)
          .named("scDriveRewrites")
      }

      def appends = {
        val chunksEnd = dataState.currentChunks.lastOption.fold(0L)(_._1.end)
        Source.fromIterator(() ⇒ dataUtils.pendingAppends(pendingWrites, chunksEnd))
          .map { case (range, chunk) ⇒ PendingChunkIO.Append(range, chunk.data.plain) }
          .named("scDriveAppends")
      }

      rewrites.concat(appends)
    }

    def finishWrite(writeStream: Source[PendingChunkIO, NotUsed]): Source[ChunkIOOperation, NotUsed] = {
      val streams = writeStream.collect {
        case PendingChunkIO.Append(range, newData) ⇒
          Source.single(Chunk(data = Data(newData)))
            .via(sc.streams.chunk.beforeWrite())
            .map((regionId, _))
            .via(sc.streams.region.writeChunks)
            .map(chunk ⇒ ChunkIOOperation.ChunkAppended(range, chunk.withoutData))

        case PendingChunkIO.Rewrite(range, chunk, patches) ⇒
          Source.single((regionId, chunk))
            .via(sc.streams.region.readChunks)
            .via(sc.streams.chunk.afterRead)
            .flatMapConcat { oldChunk ⇒
              val oldData = oldChunk.data.plain
              val newData = patches.patchChunk(range, oldData)
              Source.single(oldChunk.copy(data = Data(newData)))
                .via(sc.streams.chunk.beforeWrite())
                .map((regionId, _))
                .via(sc.streams.region.writeChunks)
                .map(chunk ⇒ ChunkIOOperation.ChunkRewritten(range, oldChunk.withoutData, chunk.withoutData))
            }
      }

      streams
        .flatMapMerge(sc.config.parallelism.write, identity)
        .named("scDriveFinishWrite")
    }

    def flush(): Future[FlushResult] = {
      val currentWrites = dataState.pendingWrites
      val writesStream = createWritesStream(currentWrites)
        .via(WritesOptimizeStage(sc.config.chunks.chunkSize))
        .named("scDriveOptimizedWrites")

      finishWrite(writesStream)
        .fold(Nil: Seq[ChunkIOOperation])(_ :+ _)
        .map(chunks ⇒ FlushResult(currentWrites, chunks))
        .runWith(Sink.head)
    }

    def cutFile(newSize: Long): Future[Long] = {
      val lastChunk = dataState.currentChunks.find(rc ⇒ rc._1.start < newSize && rc._1.end > newSize)
      val writesStream = lastChunk match {
        case Some((range, chunk)) ⇒
          Source.single((regionId, chunk))
            .via(sc.streams.region.readChunks)
            .via(sc.streams.chunk.afterRead)
            .map { chunk ⇒
              val newRange = range.fitToSize(newSize)
              val newData = chunk.data.plain.take(newRange.size.toInt)
              WriteData(newRange.start, newData)
            }

        case None ⇒
          Source.empty
      }

      writesStream
        .mapAsyncUnordered(1)(write ⇒ (self ? write).mapTo[WriteData.Success])
        .fold(NotUsed)((_, _) ⇒ NotUsed)
        .mapAsyncUnordered(1)(_ ⇒ DropChunks.unwrapFuture(self ? DropChunks(newSize)))
        .named("scDriveCutFile")
        .runWith(Sink.head)
    }

    // -----------------------------------------------------------------------
    // Revisions
    // -----------------------------------------------------------------------
    def saveNewRevision(): Future[File] = {
      val newFile = dataState.buildNewRevision()
      sc.ops.region.createFile(regionId, newFile)
    }
  }

  object dataOps {
    def applyPatch(patch: ChunkPatch): Unit = {
      dataState.pendingWrites :+= patch
    }

    def applyFlushResult(writes: Seq[ChunkPatch], ops: Seq[ChunkIOOperation]): Unit = {
      val writeSet = writes.toSet
      dataState.pendingWrites = dataState.pendingWrites.filterNot(writeSet.contains)

      val rewrittenRanges = ops.map(_.range)
      dataState.currentChunks
        .keys
        .filter(cr ⇒ rewrittenRanges.exists(_.contains(cr)))
        .foreach(dataState.currentChunks -= _)
      ops.foreach(op ⇒ dataState.currentChunks(op.range) = op.chunk)
    }

    def updateRevision(newRevision: File): Unit = {
      dataState.lastRevision = newRevision
    }

    def dropChunks(newSize: Long): Unit = {
      for (range ← dataState.currentChunks.keys if range.end > newSize)
        dataState.currentChunks -= range

      dataState.pendingWrites = dataState.pendingWrites.collect {
        case data if data.range.end <= newSize ⇒
          data

        case data if data.range.start < newSize ⇒
          val dropBytes = newSize - data.range.end
          data.copy(data.offset, data.data.dropRight(dropBytes.toInt))
      }

      dataState.lastSetSize = newSize
    }
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  object dataUtils {
    def pendingAppends(pendingWrites: Seq[ChunkPatch], chunksEnd: Long): Iterator[(ChunkRanges.Range, Chunk)] = {
      val appends = pendingWrites.filter(_.range.end > chunksEnd)
      val newSize = if (appends.isEmpty) chunksEnd else appends.map(_.range.end).max

      def appendsIterator(offset: Long, restSize: Long): Iterator[(ChunkRanges.Range, Chunk)] = {
        def padding(restSize: Long) = {
          val size = math.min(restSize, sc.config.chunks.chunkSize)
          ByteString(new Array[Byte](size.toInt))
        }

        if (restSize <= 0) {
          Iterator.empty
        } else {
          val bytes = padding(restSize)
          val chunkRange = ChunkRanges.Range(offset, offset + bytes.length)
          val patches = appends.filter(a ⇒ chunkRange.contains(a.range))
          val patchedBytes = patches.patchChunk(chunkRange, bytes)
          val chunk = Chunk(data = Data(patchedBytes))
          Iterator.single((chunkRange, chunk)) ++ appendsIterator(offset + bytes.length, restSize - bytes.length)
        }
      }

      appendsIterator(chunksEnd, newSize - chunksEnd)
    }
  }

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    case ReadData(range) ⇒
      val future = dataIO.readStream(range)
        .via(ByteStreams.concat)
        .runWith(Sink.head)
      ReadData.wrapFuture(range, future).pipeTo(sender())

    case wr: WriteData ⇒
      dataOps.applyPatch(wr)
      if (dataState.isFlushRequired) {
        log.debug("Flushing writes: {}", dataState.pendingWrites)
        val flushFuture = (self ? Flush)
          .mapTo[Flush.Success]
          .map(_ ⇒ Done)
        WriteData.wrapFuture(wr, flushFuture).pipeTo(sender())
      } else {
        sender() ! WriteData.Success(wr, Done)
      }

    case CutFile(newSize) ⇒
      log.debug("Cutting file to {}", MemorySize(newSize))
      CutFile.wrapFuture(file, dataIO.cutFile(newSize)).pipeTo(sender())

    case DropChunks(newSize) ⇒
      dataOps.dropChunks(newSize)
      sender() ! DropChunks.Success(file, newSize)

    case Flush ⇒
      actorState.pendingFlush.addWaiter(NotUsed, sender(), () ⇒ Flush.wrapFuture(file, dataIO.flush()).pipeTo(self))

    case PersistRevision ⇒
      val future = if (dataState.isRevisionModified) {
        val future = dataIO.saveNewRevision()
        future.flatMap { newFile ⇒
          if (config.fileIO.createMetadata) {
            sc.streams.file.read(regionId, newFile)
              .via(sc.streams.metadata.create(newFile.path.name))
              .via(sc.streams.metadata.writeAll(regionId, newFile.id))
              .log("drive-metadata-files")
              .runWith(Sink.seq)
              .map(_ ⇒ newFile)
              .recover { case _ ⇒ newFile }
          } else {
            Future.successful(newFile)
          }
        }
      } else {
        Future.successful(dataState.lastRevision)
      }

      future.map(RevisionUpdated).pipeTo(self)
      PersistRevision.wrapFuture(file, future).pipeTo(sender())

    case GetCurrentRevision ⇒
      val currentRevision = dataState.fixRevisionBounds()
      sender() ! GetCurrentRevision.Success(file, currentRevision)

    case result: Flush.Status ⇒
      result match {
        case Flush.Success(_, FlushResult(writes, ops)) ⇒
          log.debug("Flush finished: writes = {}, new chunks = {}", writes, ops)
          dataOps.applyFlushResult(writes, ops)

        case Flush.Failure(file, error) ⇒
          log.error(error, "File flush error: {}", file)
      }
      actorState.finishFlush(result)

    case RevisionUpdated(newFile) if newFile != dataState.lastRevision ⇒
      log.info("File revision updated: {}", newFile)
      dataOps.updateRevision(newFile)

    case ReleaseFile ⇒
      log.debug("Stopping file IO scheduler")
      if (dataState.isChunksModified) {
        val future = Flush.unwrapFuture(self ? Flush)
          .flatMap(_ ⇒ PersistRevision.unwrapFuture(self ? PersistRevision))
        ReleaseFile.wrapFuture(file, future).pipeTo(sender())
        future.map(_ ⇒ ReleaseFile).pipeTo(self)(sender())
      } else {
        sender() ! ReleaseFile.Success(file, dataState.lastRevision)
        context.stop(self)
      }

    case ReceiveTimeout ⇒
      self ! ReleaseFile
  }

  // -----------------------------------------------------------------------
  // Misc
  // -----------------------------------------------------------------------
  override def preStart(): Unit = {
    super.preStart()
    context.setReceiveTimeout(config.fileIO.flushInterval)
  }
}