package com.karasiq.shadowcloud.streams.file

import akka.NotUsed
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Source}
import akka.stream.{FlowShape, Materializer}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.{Chunk, File, Path, RegionId}
import com.karasiq.shadowcloud.ops.supervisor.RegionSupervisorOps
import com.karasiq.shadowcloud.streams.chunk.ChunkProcessingStreams
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges.RangeList
import com.karasiq.shadowcloud.streams.region.RegionStreams
import com.karasiq.shadowcloud.streams.utils.AkkaStreamUtils

import scala.concurrent.Future

object FileStreams {
  def apply(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams, supervisorOps: RegionSupervisorOps)(
      implicit m: Materializer
  ): FileStreams = {
    new FileStreams(regionStreams, chunkProcessing, supervisorOps)
  }
}

final class FileStreams(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams, supervisorOps: RegionSupervisorOps) {
  def readChunkStream(regionId: RegionId): Flow[Chunk, ByteString, NotUsed] = {
    Flow[Chunk]
      .map((regionId, _))
      .via(regionStreams.readChunks)
      // .log("chunk-stream-read")
      // .via(chunkProcessing.afterRead)
      .map(_.data.plain)
      .named("readChunkStream")
  }

  def readChunkStream(regionId: RegionId, chunks: Seq[Chunk]): Source[ByteString, NotUsed] = {
    Source.fromIterator(() ⇒ chunks.iterator).via(readChunkStream(regionId))
  }

  def readChunkStreamRanged(regionId: RegionId): Flow[(Chunk, RangeList), ByteString, NotUsed] = {
    Flow[(Chunk, RangeList)]
      .log("chunk-ranges")
      .flatMapConcat {
        case (chunk, ranges) ⇒
          Source
            .single(chunk)
            .via(readChunkStream(regionId))
            .map(ranges.slice)
      }
      .named("readChunkStreamRanged")
  }

  def readChunkStreamRanged(regionId: RegionId, chunks: Seq[Chunk], ranges: RangeList): Source[ByteString, NotUsed] = {
    Source
      .fromIterator(() ⇒ RangeList.mapChunkStream(ranges, chunks).iterator)
      .via(readChunkStreamRanged(regionId))
  }

  def read(regionId: RegionId, file: File): Source[ByteString, NotUsed] = {
    readChunkStream(regionId, file.chunks).named("readFile")
  }

  def readBy(regionId: RegionId, path: Path, select: Set[File] ⇒ File): Source[ByteString, NotUsed] = {
    Source
      .single((regionId, path))
      .via(regionStreams.findFiles)
      .map(e ⇒ select(e._2))
      .flatMapConcat(read(regionId, _))
  }

  def readMostRecent(regionId: RegionId, path: Path): Source[ByteString, NotUsed] = {
    readBy(regionId, path, FileVersions.mostRecent).named("readMostRecent")
  }

  def writeChunkStream(regionId: RegionId): Flow[ByteString, FileIndexer.Result, NotUsed] = {
    Flow[ByteString]
      .via(AkkaStreamUtils.extractUpstream)
      .zip(Source.future(supervisorOps.getRegionConfig(regionId)))
      .flatMapConcat { case (stream, config) =>
        val matSink = Flow
          .fromGraph(chunkProcessing.split(config.chunkSize.getOrElse(chunkProcessing.defaultChunkSize)))
          .via(chunkProcessing.beforeWrite())
          // .map(c ⇒ c.copy(data = c.data.copy(plain = ByteString.empty))) // Memory optimization
          .map((regionId, _))
          .via(regionStreams.writeChunks)
          // .log("chunk-stream-write")
          .toMat(chunkProcessing.index())(Keep.right)

        val graph = GraphDSL.create(matSink) { implicit builder ⇒ matSink ⇒
          import GraphDSL.Implicits._
          val extractResult = builder.add(Flow[Future[FileIndexer.Result]].flatMapConcat(Source.future))

          builder.materializedValue ~> extractResult
          FlowShape(matSink.in, extractResult.out)
        }

        stream.via(graph)
      }
      .named("writeChunkStream")
  }

  def write(regionId: RegionId, path: Path): Flow[ByteString, File, NotUsed] = {
    writeChunkStream(regionId)
      .map((regionId, path, _))
      .via(regionStreams.createFile)
      .named("writeFile")
  }
}
