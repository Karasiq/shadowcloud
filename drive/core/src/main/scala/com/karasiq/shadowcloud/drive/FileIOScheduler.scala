package com.karasiq.shadowcloud.drive

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

import akka.{Done, NotUsed}
import akka.actor.{Actor, ActorLogging, Props, ReceiveTimeout}
import akka.pattern.{ask, pipe}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.ShadowCloud
import com.karasiq.shadowcloud.actors.utils.{MessageStatus, PendingOperation}
import com.karasiq.shadowcloud.drive.FileIOScheduler._
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
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
    val range = ChunkRanges.Range(offset, offset + data.length)

    override def toString: String = s"WriteData($range)"
  }
  object WriteData extends MessageStatus[WriteData, Done]

  final case class CutFile(size: Long) extends Message
  object CutFile extends MessageStatus[File, Long]

  final case object Flush extends Message with MessageStatus[File, FlushResult]
  final case object PersistRevision extends Message with MessageStatus[File, File]

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
  sealed trait IOOperation {
    def range: ChunkRanges.Range
    def chunk: Chunk
  }
  object IOOperation {
    final case class ChunkRewritten(range: ChunkRanges.Range, oldChunk: Chunk, chunk: Chunk) extends IOOperation
    final case class ChunkAppended(range: ChunkRanges.Range, chunk: Chunk) extends IOOperation
  }

  final case class FlushResult(writes: Seq[WriteData], ops: Seq[IOOperation])

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
  implicit val materializer = ActorMaterializer()
  val sc = ShadowCloud()
  import sc.implicits.defaultTimeout

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  var currentChunks = mutable.TreeMap(ChunkRanges.RangeList.zipWithRange(file.chunks).map(_.swap):_*)
  var pendingWrites = Seq.empty[WriteData]
  val pendingFlush = PendingOperation[NotUsed]
  var lastFlush = 0L
  var lastRevision = file

  // -----------------------------------------------------------------------
  // Read
  // -----------------------------------------------------------------------
  def readStream(range: ChunkRanges.Range): Source[ByteString, NotUsed] = {
    val currentWrites = this.pendingWrites
    val patches = currentWrites.filter(wr ⇒ range.contains(wr.range))

    def chunksStream = {
      sc.streams.file.readChunkStreamRanged(regionId, currentChunks.values.toVector, range)
        .statefulMapConcat { () ⇒
          var position = range.start
          bytes ⇒ {
            val range = ChunkRanges.Range(position, position + bytes.length)
            val patched = patchChunk(range, bytes, patches.filter(wr ⇒ range.contains(wr.range)))
            position += bytes.length
            Vector(patched)
          }
        }
        .named("scDriveReadChunks")
    }

    def appendsStream = {
      val chunksEnd = currentChunks.lastOption.fold(0L)(_._1.end)
      if (currentWrites.forall(_.range.end <= chunksEnd)) {
        Source.empty[ByteString]
      } else {
        val fileEnd = math.max(chunksEnd, math.min(range.end, currentWrites.map(_.range.end).max))
        val appendsRange = ChunkRanges.Range(chunksEnd, fileEnd)
        Source
          .fromIterator(() ⇒ pendingAppends(currentWrites, chunksEnd))
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
  def writeStream(pendingWrites: Seq[WriteData]): Source[IOOperation, NotUsed] = {
    def rewrites = {
      val writesByChunk = (for {
        write ← pendingWrites
        (range, chunk) ← currentChunks if range.contains(write.range)
      } yield ((chunk, range), write)).groupBy(_._1).mapValues(_.map(_._2))

      Source(writesByChunk)
        .flatMapMerge(sc.config.parallelism.write, { case ((chunk, chunkRange), patches) ⇒
          Source.single((regionId, chunk))
            .via(sc.streams.region.readChunks)
            .via(sc.streams.chunk.afterRead)
            .flatMapConcat { oldChunk ⇒
              val oldData = oldChunk.data.plain
              val newData = patchChunk(chunkRange, oldData, patches)
              Source.single(oldChunk.copy(data = Data(newData)))
                .via(sc.streams.chunk.beforeWrite())
                .map((regionId, _))
                .via(sc.streams.region.writeChunks)
                .map(chunk ⇒ IOOperation.ChunkRewritten(chunkRange, oldChunk.withoutData, chunk.withoutData))
            }
        })
        .named("scDriveRewrite")
    }

    def appends = {
      val chunksEnd = currentChunks.lastOption.fold(0L)(_._1.end)
      Source.fromIterator(() ⇒ pendingAppends(pendingWrites, chunksEnd))
        .flatMapMerge(sc.config.parallelism.write, { case (range, chunk) ⇒
          Source.single(chunk)
            .via(sc.streams.chunk.beforeWrite())
            .map((regionId, _))
            .via(sc.streams.region.writeChunks)
            .map(chunk ⇒ IOOperation.ChunkAppended(range, chunk.withoutData))
        })
    }

    rewrites.concat(appends)
  }

  def cutFile(newSize: Long): Future[Long] = {
    val lastChunk = currentChunks.find(rc ⇒ rc._1.start < newSize && rc._1.end > newSize)
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

  def dropChunks(newSize: Long): Unit = {
    for (range ← currentChunks.keys if range.end > newSize)
      currentChunks -= range

    pendingWrites = pendingWrites.collect {
      case data if data.range.end <= newSize ⇒
        data

      case data if data.range.start < newSize ⇒
        val dropBytes = newSize - data.range.end
        data.copy(data.offset, data.data.dropRight(dropBytes.toInt))
    }
  }

  def flush(): Future[FlushResult] = {
    val currentWrites = pendingWrites
    writeStream(currentWrites)
      .fold(Nil: Seq[IOOperation])(_ :+ _)
      .map(chunks ⇒ FlushResult(currentWrites, chunks))
      .runWith(Sink.head)
  }

  def isFlushRequired: Boolean = {
    (System.nanoTime() - lastFlush).nanos > config.fileIO.flushInterval ||
      pendingWrites.map(_.range.size).sum > config.fileIO.flushLimit
  }

  def isChunksModified: Boolean = {
    pendingWrites.nonEmpty || currentChunks != lastRevision.chunks
  }

  // -----------------------------------------------------------------------
  // Revisions
  // -----------------------------------------------------------------------
  def updateRevision(): Future[File] = {
    val newChunks = currentChunks.values.toVector
    val newSize = newChunks.map(_.checksum.size).sum
    val newEncSize = newChunks.map(_.checksum.encSize).sum
    val newChecksum = Checksum(HashingMethod.none, HashingMethod.none, newSize, ByteString.empty, newEncSize, ByteString.empty)
    val newFile = File.modified(lastRevision, newChecksum, newChunks)
    sc.ops.region.createFile(regionId, newFile)
  }

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    case ReadData(range) ⇒
      val future = readStream(range)
        .via(ByteStreams.concat)
        .runWith(Sink.head)
      ReadData.wrapFuture(range, future).pipeTo(sender())

    case wr: WriteData ⇒
      pendingWrites :+= wr
      if (isFlushRequired) {
        log.debug("Flushing writes: {}", pendingWrites)
        val flushFuture = (self ? Flush)
          .mapTo[Flush.Success]
          .map(_ ⇒ Done)
        WriteData.wrapFuture(wr, flushFuture).pipeTo(sender())
      } else {
        sender() ! WriteData.Success(wr, Done)
      }

    case CutFile(newSize) ⇒
      log.info("Cutting file to {}", MemorySize(newSize))
      CutFile.wrapFuture(file, cutFile(newSize)).pipeTo(sender())

    case DropChunks(newSize) ⇒
      dropChunks(newSize)
      sender() ! DropChunks.Success(file, newSize)

    case Flush ⇒
      pendingFlush.addWaiter(NotUsed, sender(), () ⇒ Flush.wrapFuture(file, flush()).pipeTo(self))

    case PersistRevision ⇒
      val future = updateRevision()
      future.map(RevisionUpdated).pipeTo(self)
      PersistRevision.wrapFuture(file, future).pipeTo(sender())

    case result: Flush.Status ⇒
      result match {
        case Flush.Success(_, FlushResult(writes, ops)) ⇒
          log.debug("Flush finished: writes = {}, new chunks = {}", writes, ops)
          val writeSet = writes.toSet
          pendingWrites = pendingWrites.filterNot(writeSet.contains)
          ops.foreach(op ⇒ currentChunks(op.range) = op.chunk)
          lastFlush = System.nanoTime()

        case Flush.Failure(file, error) ⇒
          log.error(error, "File flush error: {}", file)
      }

      pendingFlush.finish(NotUsed, result)

    case RevisionUpdated(newFile) ⇒
      log.info("File revision updated: {}", newFile)
      lastRevision = newFile

    case ReceiveTimeout ⇒
      if (isChunksModified) {
        (self ? Flush).foreach(_ ⇒ self ! PersistRevision)
      } else {
        log.debug("Stopping file IO scheduler")
        context.stop(self)
      }
  }

  // -----------------------------------------------------------------------
  // Utils
  // -----------------------------------------------------------------------
  def pendingAppends(pendingWrites: Seq[WriteData], chunksEnd: Long): Iterator[(ChunkRanges.Range, Chunk)] = {
    val appends = pendingWrites.filter(_.range.end > chunksEnd)
    val newSize = appends.lastOption.fold(chunksEnd)(_.range.end)

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
        val patchedBytes = patchChunk(chunkRange, bytes, patches)
        val chunk = Chunk(data = Data(patchedBytes))
        Iterator.single((chunkRange, chunk)) ++ appendsIterator(offset + bytes.length, restSize - bytes.length)
      }
    }

    appendsIterator(chunksEnd, newSize - chunksEnd)
  }

  def patchChunk(dataRange: ChunkRanges.Range, data: ByteString, patches: Seq[WriteData]) = {
    patches.foldLeft(data) { case (data, write) ⇒
      val relRange = write.range.relativeTo(dataRange)
      val offset = dataRange.relativeTo(write.range)
      ChunkRanges.Range.patch(data, relRange, offset.slice(write.data))
    }
  }

  // -----------------------------------------------------------------------
  // Misc
  // -----------------------------------------------------------------------
  override def preStart(): Unit = {
    super.preStart()
    context.setReceiveTimeout(config.fileIO.flushInterval)
  }
}