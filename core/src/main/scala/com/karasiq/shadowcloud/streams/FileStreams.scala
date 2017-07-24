package com.karasiq.shadowcloud.streams

import scala.language.postfixOps

import akka.NotUsed
import akka.stream.{FlowShape, Materializer}
import akka.stream.scaladsl.{Flow, GraphDSL, Keep, Source}
import akka.util.ByteString

import com.karasiq.shadowcloud.index.{Chunk, File, Path}
import com.karasiq.shadowcloud.index.diffs.FileVersions

object FileStreams {
  def apply(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams)(implicit m: Materializer): FileStreams = {
    new FileStreams(regionStreams, chunkProcessing)
  }
}

final class FileStreams(regionStreams: RegionStreams, chunkProcessing: ChunkProcessingStreams) {
  def readChunkStream(regionId: String, chunks: Seq[Chunk]): Source[ByteString, NotUsed] = {
    Source(chunks.toVector)
      .map((regionId, _))
      .via(regionStreams.readChunks)
      .via(chunkProcessing.afterRead)
      .map(_.data.plain)
  }

  def read(regionId: String, file: File): Source[ByteString, NotUsed] = { // TODO: Byte ranges
    Source.single(file)
      .mapConcat(_.chunks.toVector)
      .map((regionId, _))
      .via(regionStreams.readChunks)
      .via(chunkProcessing.afterRead)
      .map(_.data.plain)
  }

  def readBy(regionId: String, path: Path, select: Set[File] ⇒ File): Source[ByteString, NotUsed] = {
    Source.single((regionId, path))
      .via(regionStreams.findFiles)
      .map(e ⇒ select(e._2))
      .flatMapConcat(read(regionId, _))
  }

  def readMostRecent(regionId: String, path: Path): Source[ByteString, NotUsed] = {
    readBy(regionId, path, FileVersions.mostRecent)
  }

  def write(regionId: String, path: Path): Flow[ByteString, File, NotUsed] = {
    val matSink = Flow.fromGraph(chunkProcessing.split()) // TODO: Chunk size config
      .via(chunkProcessing.beforeWrite())
      .map((regionId, _))
      .via(regionStreams.writeChunks)
      .toMat(chunkProcessing.index())(Keep.right)

    val graph = GraphDSL.create(matSink) { implicit builder ⇒ matSink ⇒
      import GraphDSL.Implicits._
      val addFileFlow = builder.add(
        Flow[FileIndexer.Result]
          .map((regionId, path, _))
          .via(regionStreams.addFile)
      )

      builder.materializedValue.flatMapConcat(Source.fromFuture) ~> addFileFlow
      FlowShape(matSink.in, addFileFlow.out)
    }

    Flow.fromGraph(graph).mapMaterializedValue(_ ⇒ NotUsed)
  }
}
