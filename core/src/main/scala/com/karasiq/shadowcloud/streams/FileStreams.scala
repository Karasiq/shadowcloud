package com.karasiq.shadowcloud.streams

import scala.concurrent.Future
import scala.language.postfixOps

import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.index.{File, Path}
import com.karasiq.shadowcloud.index.diffs.FileVersions

object FileStreams {
  def apply(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams)(implicit m: Materializer): FileStreams = {
    new FileStreams(regionStreams, chunkProcessing)
  }
}

final class FileStreams(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams)(implicit m: Materializer) {
  def readBy(regionId: String, path: Path, select: Set[File] ⇒ File): Source[ByteString, NotUsed] = {
    Source.single((regionId, path))
      .via(regionStreams.findFiles)
      .map(e ⇒ select(e._2))
      .mapConcat(_.chunks.toVector)
      .map((regionId, _))
      .via(regionStreams.readChunks)
      .via(chunkProcessing.afterRead)
      .map(_.data.plain)
  }

  def read(regionId: String, path: Path): Source[ByteString, NotUsed] = { // TODO: Byte ranges
    readBy(regionId, path, FileVersions.mostRecent)
  }

  def write(regionId: String, path: Path): Sink[ByteString, Future[File]] = {
    Flow.fromGraph(chunkProcessing.split()) // TODO: Chunk size config
      .via(chunkProcessing.beforeWrite())
      .map((regionId, _))
      .via(regionStreams.writeChunks)
      .toMat(chunkProcessing.index())(Keep.right)
      .mapMaterializedValue { future ⇒
        Source.fromFuture(future)
          .map((regionId, path, _))
          .via(regionStreams.addFile)
          .runWith(Sink.head)
      }
  }
}
