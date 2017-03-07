package com.karasiq.shadowcloud.streams

import akka.NotUsed
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.util.ByteString
import com.karasiq.shadowcloud.index.{File, Path}
import com.karasiq.shadowcloud.utils.MemorySize

import scala.concurrent.Future
import scala.language.postfixOps

object FileStreams {
  def apply(regionStreams: RegionStreams, chunkProcessing: ChunkProcessing)(implicit am: ActorMaterializer): FileStreams = {
    new FileStreams(regionStreams, chunkProcessing)
  }
}

class FileStreams(regionStreams: RegionStreams, chunkProcessing: ChunkProcessing)(implicit am: ActorMaterializer) {
  def read(regionId: String, path: Path): Source[ByteString, NotUsed] = { // TODO: Byte ranges
    Source.single((regionId, path))
      .via(regionStreams.findFile)
      .mapConcat(_.chunks.toVector)
      .map((regionId, _))
      .via(regionStreams.readChunks)
      .via(chunkProcessing.afterRead)
      .map(_.data.plain)
  }

  def write(regionId: String, path: Path): Sink[ByteString, Future[File]] = {
    Flow.fromGraph(ChunkSplitter(MemorySize.MB)) // TODO: Chunk size config
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
