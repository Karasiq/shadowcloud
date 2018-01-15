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

  final case object Flush extends MessageStatus[File, FlushResult]
  final case object PersistRevision extends MessageStatus[File, File]

  // -----------------------------------------------------------------------
  // Internal Messages
  // -----------------------------------------------------------------------
  private sealed trait InternalMessage extends Message
  private final case class FlushFinished(flushResult: FlushResult) extends InternalMessage
  private final case class RevisionUpdated(newFile: File) extends InternalMessage

  // -----------------------------------------------------------------------
  // Misc
  // -----------------------------------------------------------------------
  final case class FlushResult(writes: Seq[WriteData], newChunks: Seq[(Chunk, Chunk)])

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
      .named("scDriveRead")
  }

  // -----------------------------------------------------------------------
  // Write
  // -----------------------------------------------------------------------
  def writeStream(pendingWrites: Seq[WriteData]): Source[(Chunk, Chunk), NotUsed] = {
    val rangedChunks = ChunkRanges.RangeList.zipWithRange(currentChunks)
    val writesByChunk = (for {
      write ← pendingWrites
      (chunk, range) ← rangedChunks if range.contains(write.range)
    } yield ((chunk, range), write)).groupBy(_._1).mapValues(_.map(_._2))

    Source(writesByChunk)
      .flatMapMerge(sc.config.parallelism.write, { case ((chunk, range), writes) ⇒
        val sourceFuture = sc.ops.region.readChunk(regionId, chunk).map { oldChunk ⇒
          val oldData = oldChunk.data.plain
          val newData = writes.foldLeft(oldData) { case (data, write) ⇒
            val relRange = write.range.relativeTo(range)
            val patchData = relRange.slice(write.data)
            ChunkRanges.Range.patch(data, relRange, patchData)
          }

          Source.single(oldChunk.copy(data = Data(newData)))
            .via(sc.streams.chunk.beforeWrite())
            .map((regionId, _))
            .via(sc.streams.region.writeChunks)
            .map((oldChunk, _))
        }

        Source.fromFutureSource(sourceFuture)
      })
      .named("scDriveWrite")
  }

  def flush(): Future[FlushResult] = {
    val currentWrites = pendingWrites
    writeStream(currentWrites)
      .fold(Nil: Seq[(Chunk, Chunk)])(_ :+ _)
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
    val newFile = File.modified(lastRevision, file.checksum.copy(hash = ByteString.empty, encHash = ByteString.empty), currentChunks)
    sc.ops.region.createFile(regionId, newFile)
  }

  // -----------------------------------------------------------------------
  // Receive
  // -----------------------------------------------------------------------
  def receive: Receive = {
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

    case ReadData(range) ⇒
      val currentSender = sender()
      readStream(range)
        .via(ByteStreams.concat)
        .alsoTo(Sink.onComplete(_.failed.foreach(error ⇒ currentSender ! ReadData.Failure(range, error))))
        .runWith(Sink.foreach(data ⇒ currentSender ! ReadData.Success(range, data)))

    case FlushFinished(FlushResult(writes, newChunks)) ⇒
      log.debug("Flush finished: writes = {}, new chunks = {}", writes, newChunks)
      val writeSet = writes.toSet
      pendingWrites = pendingWrites.filterNot(writeSet.contains)

      val chunksMap = newChunks.toMap
      currentChunks = currentChunks.map(chunk ⇒ chunksMap.getOrElse(chunk, chunk))

    case Flush ⇒
      Flush.wrapFuture(file, flush()).pipeTo(sender())

    case PersistRevision ⇒
      val future = updateRevision()
      future.map(RevisionUpdated).pipeTo(self)
      PersistRevision.wrapFuture(file, future).pipeTo(sender())

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
  // Misc
  // -----------------------------------------------------------------------
  override def preStart(): Unit = {
    super.preStart()
    context.setReceiveTimeout(config.fileIO.flushInterval)
  }
}