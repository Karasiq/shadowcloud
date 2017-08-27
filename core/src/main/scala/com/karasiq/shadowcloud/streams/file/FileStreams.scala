package com.karasiq.shadowcloud.streams.file

import scala.concurrent.Future
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.index.{Chunk, File, Path}
import com.karasiq.shadowcloud.index.diffs.FileVersions
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.streams.chunk.ChunkProcessingStreams
import com.karasiq.shadowcloud.streams.chunk.ChunkRanges.RangeList
import com.karasiq.shadowcloud.streams.region.RegionStreams

object FileStreams {
  def apply(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams)(implicit m: Materializer): FileStreams = {
    new FileStreams(regionStreams, chunkProcessing)
  }
}

final class FileStreams(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams) {
  def readChunkStream(regionId: RegionId, chunks: Seq[Chunk]): Source[ByteString, NotUsed] = {
    Source.fromIterator(() ⇒ chunks.iterator)
      .map((regionId, _))
      .via(regionStreams.readChunks)
      // .log("chunk-stream-read")
      .via(chunkProcessing.afterRead)
      .map(_.data.plain)
      .named("readChunkStream")
  }

  def readChunkStreamRanged(regionId: RegionId, chunks: Seq[Chunk], ranges: RangeList): Source[ByteString, NotUsed] = {
    Source.fromIterator(() ⇒ RangeList.mapChunkStream(ranges, chunks).iterator)
      .log("chunk-ranges")
      .flatMapConcat { case (chunk, ranges) ⇒
        readChunkStream(regionId, Vector(chunk))
          .map(ranges.slice)
      }
      .named("readChunkStreamRanged")
  }

  def read(regionId: RegionId, file: File): Source[ByteString, NotUsed] = {
    readChunkStream(regionId, file.chunks)
      .named("readFile")
  }

  def readBy(regionId: RegionId, path: Path, select: Set[File] ⇒ File): Source[ByteString, NotUsed] = {
    Source.single((regionId, path))
      .via(regionStreams.findFiles)
      .map(e ⇒ select(e._2))
      .flatMapConcat(read(regionId, _))
  }

  def readMostRecent(regionId: RegionId, path: Path): Source[ByteString, NotUsed] = {
    readBy(regionId, path, FileVersions.mostRecent)
  }

  def writeChunkStream(regionId: RegionId): Flow[ByteString, FileIndexer.Result, NotUsed] = {
    val matSink = Flow.fromGraph(chunkProcessing.split()) // TODO: Chunk size config
      .via(chunkProcessing.beforeWrite())
      .map((regionId, _))
      .via(regionStreams.writeChunks)
      .log("chunk-stream-write")
      .toMat(chunkProcessing.index())(Keep.right)

    val graph = GraphDSL.create(matSink) { implicit builder ⇒ matSink ⇒
      import GraphDSL.Implicits._
      val extractResult = builder.add(Flow[Future[FileIndexer.Result]].flatMapConcat(Source.fromFuture))

      builder.materializedValue ~> extractResult
      FlowShape(matSink.in, extractResult.out)
    }

    Flow.fromGraph(graph)
      .mapMaterializedValue(_ ⇒ NotUsed)
      .named("writeChunkStream")
  }

  def write(regionId: RegionId, path: Path): Flow[ByteString, File, NotUsed] = {
    writeChunkStream(regionId)
      .map((regionId, path, _))
      .via(regionStreams.createFile)
      .named("writeFile")
  }
}
