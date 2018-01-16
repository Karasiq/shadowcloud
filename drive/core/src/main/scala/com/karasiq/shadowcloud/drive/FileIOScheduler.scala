package com.karasiq.shadowcloud.drive

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
import com.karasiq.shadowcloud.actors.utils.MessageStatus
import com.karasiq.shadowcloud.drive.FileIOScheduler._
import com.karasiq.shadowcloud.drive.config.SCDriveConfig
import com.karasiq.shadowcloud.model.{Chunk, Data, File, RegionId}
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
  private final case class FlushFinished(flushResult: FlushResult) extends InternalMessage
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
  var currentChunks = file.chunks
  var pendingWrites = Seq.empty[WriteData]
  var lastWrite = 0L
  var lastRevision = file

  // -----------------------------------------------------------------------
  // Read
  // -----------------------------------------------------------------------
  def readStream(range: ChunkRanges.Range): Source[ByteString, NotUsed] = {
    val patches = pendingWrites.filter(wr ⇒ range.contains(wr.range)).map { wr ⇒
      val start = math.min(math.max(wr.range.start, range.start), range.end)
      val end = math.min(wr.range.end, range.end)
      val patchRange = ChunkRanges.Range(start, end)
      (patchRange, patchRange.relativeTo(wr.range).slice(wr.data))
    }

    def chunksStream = {
      sc.streams.file.readChunkStreamRanged(regionId, currentChunks, range)
        .statefulMapConcat { () ⇒
          var position = range.start
          bytes ⇒ {
            val range = ChunkRanges.Range(position, position + bytes.length)
            val patched = patches.filter(wr ⇒ range.contains(wr._1)).foldLeft(bytes) { case (bytes, (patchRange, patchBytes)) ⇒
              val fixRange = patchRange.relativeTo(range).fitToSize(bytes.length)
              val fixBytes = fixRange.slice(patchBytes)
              ChunkRanges.Range.patch(bytes, fixRange, fixBytes)
            }

            position += bytes.length
            Vector(patched)
          }
        }
        .named("scDriveReadChunks")
    }

    def appendsStream = {
      val chunksEnd = currentChunks.map(_.checksum.size).sum
      val appendsRange = ChunkRanges.Range(chunksEnd, range.end)
      Source
        .fromIterator(() ⇒ pendingAppends(pendingWrites, chunksEnd))
        .map { case (range, chunk) ⇒
          val selectedRange = range.relativeTo(appendsRange)
          selectedRange.slice(chunk.data.plain)
        }
        // .via(ByteStreams.limit(appendsRange.size))
        .named("scDriveReadAppends")
    }

    chunksStream.concat(appendsStream).named("scDriveRead")
  }

  // -----------------------------------------------------------------------
  // Write
  // -----------------------------------------------------------------------
  def writeStream(pendingWrites: Seq[WriteData]): Source[IOOperation, NotUsed] = {
    val rangedChunks = ChunkRanges.RangeList.zipWithRange(currentChunks)

    def rewrites = {
      val writesByChunk = (for {
        write ← pendingWrites
        (chunk, range) ← rangedChunks if range.contains(write.range)
      } yield ((chunk, range), write)).groupBy(_._1).mapValues(_.map(_._2))

      Source(writesByChunk)
        .flatMapMerge(sc.config.parallelism.write, { case ((chunk, chunkRange), patches) ⇒
          val sourceFuture = sc.ops.region.readChunk(regionId, chunk).map { oldChunk ⇒
            val oldData = oldChunk.data.plain
            val newData = patchChunk(chunkRange, oldData, patches)

            Source.single(oldChunk.copy(data = Data(newData)))
              .via(sc.streams.chunk.beforeWrite())
              .map((regionId, _))
              .via(sc.streams.region.writeChunks)
              .map(chunk ⇒ IOOperation.ChunkRewritten(chunkRange, oldChunk.withoutData, chunk.withoutData))
          }

          Source.fromFutureSource(sourceFuture)
        })
        .named("scDriveRewrite")
    }

    def appends = {
      val chunksEnd = rangedChunks.lastOption.fold(0L)(_._2.end)
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

  def cutFile(newSize: Long): Unit = {
    val rangedChunks = ChunkRanges.RangeList.zipWithRange(currentChunks)
    val newChunks = rangedChunks.filter(_._2.end < newSize)

    rangedChunks.find(rc ⇒ rc._2.start < newSize && rc._2.end > newSize).foreach { case (chunk, range) ⇒
      val newRange = range.fitToSize(newSize)
      val newData = chunk.data.plain.dropRight((newSize - range.end).toInt)
      pendingWrites :+= WriteData(newRange.start, newData)
    }

    currentChunks = newChunks.map(_._1)
  }

  def flush(): Future[FlushResult] = {
    val currentWrites = pendingWrites
    writeStream(currentWrites)
      .fold(Nil: Seq[IOOperation])(_ :+ _)
      .map(chunks ⇒ FlushResult(currentWrites, chunks))
      .runWith(Sink.head)
  }

  def isFlushRequired: Boolean = {
    (System.nanoTime() - lastWrite).nanos > config.fileIO.flushInterval ||
      pendingWrites.map(_.range.size).sum > config.fileIO.flushLimit
  }

  def isChunksModified: Boolean = {
    pendingWrites.nonEmpty || currentChunks != lastRevision.chunks
  }

  // -----------------------------------------------------------------------
  // Revisions
  // -----------------------------------------------------------------------
  def updateRevision(): Future[File] = {
    val newSize = currentChunks.map(_.checksum.size).sum
    val newEncSize = currentChunks.map(_.checksum.encSize).sum
    val checksum = file.checksum.copy(size = newSize, encSize = newEncSize, hash = ByteString.empty, encHash = ByteString.empty)
    val newFile = File.modified(lastRevision, checksum, currentChunks)
    sc.ops.region.createFile(regionId, newFile)
  }

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
    case ReadData(range) ⇒
      val currentSender = sender()
      readStream(range)
        .via(ByteStreams.concat)
        .alsoTo(Sink.onComplete(_.failed.foreach(error ⇒ currentSender ! ReadData.Failure(range, error))))
        .runWith(Sink.foreach(data ⇒ currentSender ! ReadData.Success(range, data)))

    case wr: WriteData ⇒
      lastWrite = System.nanoTime()
      pendingWrites :+= wr
      if (isFlushRequired) {
        log.debug("Flushing writes: {}", pendingWrites)
        val flushFuture = flush()
        flushFuture.map(FlushFinished).pipeTo(self)
        flushFuture.map(_ ⇒ WriteData.Success(wr, Done)).pipeTo(sender())
      } else {
        sender() ! WriteData.Success(wr, Done)
      }

    case CutFile(newSize) ⇒
      log.info("Cutting file to {}", MemorySize(newSize))
      cutFile(newSize)
      sender() ! CutFile.Success(file, newSize)

    case Flush ⇒
      Flush.wrapFuture(file, flush()).pipeTo(sender())

    case PersistRevision ⇒
      val future = updateRevision()
      future.map(RevisionUpdated).pipeTo(self)
      PersistRevision.wrapFuture(file, future).pipeTo(sender())

    case FlushFinished(FlushResult(writes, ops)) ⇒
      log.debug("Flush finished: writes = {}, new chunks = {}", writes, ops)
      val writeSet = writes.toSet
      pendingWrites = pendingWrites.filterNot(writeSet.contains)

      val chunkReplacements = ops.collect { case IOOperation.ChunkRewritten(_, oldChunk, chunk) ⇒ (oldChunk, chunk) }.toMap
      val chunkAppends = ops.collect { case IOOperation.ChunkAppended(_, chunk) ⇒ chunk }
      currentChunks = currentChunks.map(chunk ⇒ chunkReplacements.getOrElse(chunk, chunk)) ++ chunkAppends

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
        val size = math.min(currentChunks.headOption.fold(sc.config.chunks.chunkSize.toLong)(_.checksum.size), restSize)
        ByteString(new Array[Byte](size.toInt))
      }

      if (restSize <= 0) {
        Iterator.empty
      } else {
        val bytes = padding(restSize)
        val range = ChunkRanges.Range(offset, offset + bytes.length)
        val patches = appends.filter(a ⇒ range.contains(a.range))
        val patchedBytes = patchChunk(range, bytes, patches)
        val chunk = Chunk(data = Data(patchedBytes))
        Iterator.single((range, chunk)) ++ appendsIterator(offset + bytes.length, restSize - bytes.length)
      }
    }

    appendsIterator(chunksEnd, newSize - chunksEnd)
  }

  def patchChunk(range: ChunkRanges.Range, data: ByteString, patches: Seq[WriteData]) = {
    patches.foldLeft(data) { case (data, write) ⇒
      val relRange = write.range.relativeTo(range)
      val patchData = relRange.slice(write.data)
      ChunkRanges.Range.patch(data, relRange, patchData)
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