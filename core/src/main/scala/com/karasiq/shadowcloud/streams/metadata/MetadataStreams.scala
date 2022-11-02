package com.karasiq.shadowcloud.streams.metadata

import java.util.UUID

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Source, Zip}
import akka.util.ByteString
import com.karasiq.shadowcloud.compression.StreamCompression
import com.karasiq.shadowcloud.config.{MetadataConfig, SerializationConfig}
import com.karasiq.shadowcloud.metadata.Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataUtils}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.ops.region.RegionOps
import com.karasiq.shadowcloud.providers.MetadataModuleRegistry
import com.karasiq.shadowcloud.serialization.{SerializationModule, StreamSerialization}
import com.karasiq.shadowcloud.streams.file.{FileIndexer, FileStreams}
import com.karasiq.shadowcloud.streams.region.RegionStreams
import com.karasiq.shadowcloud.streams.utils.{AkkaStreamUtils, ByteStreams}

import scala.concurrent.Future

private[shadowcloud] object MetadataStreams {
  def apply(
      regionOps: RegionOps,
      regionStreams: RegionStreams,
      fileStreams: FileStreams,
      metadataConfig: MetadataConfig,
      metadataModules: MetadataModuleRegistry,
      serializationConfig: SerializationConfig,
      serialization: SerializationModule
  ): MetadataStreams = {
    new MetadataStreams(regionOps, regionStreams, fileStreams, metadataConfig, metadataModules, serializationConfig, serialization)
  }
}

private[shadowcloud] final class MetadataStreams(
    regionOps: RegionOps,
    regionStreams: RegionStreams,
    fileStreams: FileStreams,
    metadataConfig: MetadataConfig,
    metadataModules: MetadataModuleRegistry,
    serializationConfig: SerializationConfig,
    serialization: SerializationModule
) {

  def keys(regionId: RegionId): Source[FileId, NotUsed] = {
    Source
      .future(regionOps.getFolder(regionId, MetadataUtils.MetadataFolder))
      .recover { case _ ⇒ Folder(MetadataUtils.MetadataFolder) }
      .mapConcat(_.folders.map(UUID.fromString))
      .named("metadataFileKeys")
  }

  def write(regionId: RegionId, fileId: FileId, disposition: MDDisposition): Flow[Metadata, File, NotUsed] = {
    internalStreams
      .preWriteFile(regionId)
      .map((regionId, MetadataUtils.getFilePath(fileId, disposition), _))
      .via(regionStreams.createFile)
      .named("metadataWrite")
  }

  def writeAll(regionId: RegionId, fileId: FileId): Flow[Metadata, File, NotUsed] = {
    internalStreams
      .preWriteAll(regionId)
      .map { case (disposition, result) ⇒ (regionId, MetadataUtils.getFilePath(fileId, disposition), result) }
      .via(regionStreams.createFile)
      .named("metadataWriteAll")
  }

  def list(regionId: RegionId, fileId: FileId): Future[Folder] = {
    val path = MetadataUtils.getFolderPath(fileId)
    regionOps.getFolder(regionId, path)
  }

  def read(regionId: RegionId, fileId: FileId, disposition: MDDisposition): Source[Metadata, NotUsed] = {
    internalStreams
      .readMetadataFile(regionId, fileId, disposition)
      .named("metadataRead")
  }

  def delete(regionId: RegionId, fileId: FileId): Future[Folder] = {
    val path = MetadataUtils.getFolderPath(fileId)
    regionOps.deleteFolder(regionId, path)
  }

  def create(fileName: String, sizeLimit: Long = metadataConfig.fileSizeLimit): Flow[ByteString, Metadata, NotUsed] = {
    val graph = GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._

      val bytesInput     = builder.add(Broadcast[ByteString](2))
      val extractStream  = builder.add(Flow[ByteString].async.via(AkkaStreamUtils.extractUpstream))
      val getContentType = builder.add(Flow[ByteString].async.via(MimeDetectorStream(metadataModules, fileName, metadataConfig.mimeProbeSize)))

      val zipStreamAndMime = builder.add(Zip[Source[ByteString, NotUsed], String])

      val parseMetadata = builder.add(
        Flow[(Source[ByteString, NotUsed], String)]
          .flatMapConcat { case (bytes, contentType) ⇒
            if (metadataModules.canParse(fileName, contentType)) {
              bytes.via(metadataModules.parseMetadata(fileName, contentType))
            } else {
              bytes.via(AkkaStreamUtils.cancelUpstream(Source.empty)) // Bytes stream should be cancelled
            }
          }
      )

      bytesInput ~> getContentType
      bytesInput ~> extractStream
      extractStream ~> zipStreamAndMime.in0
      getContentType ~> zipStreamAndMime.in1
      zipStreamAndMime.out ~> parseMetadata
      FlowShape(bytesInput.in, parseMetadata.out)
    }

    Flow[ByteString]
      .via(ByteStreams.truncate(sizeLimit))
      .via(graph)
      .recoverWithRetries(1, { case _ ⇒ Source.empty })
      .named("metadataCreate")
  }

  def writeFileAndMetadata(
      regionId: RegionId,
      path: Path,
      metadataSizeLimit: Long = metadataConfig.fileSizeLimit
  ): Flow[ByteString, (File, Seq[File]), NotUsed] = {
    // Writes the chunk stream before actual file path is known
    val writeStream = Flow[ByteString]
      .via(fileStreams.write(regionId, path))
      .async

    val createMetadataStream = Flow[ByteString]
      .buffer(5, OverflowStrategy.backpressure) // Buffer byte chunks
      .via(create(path.name, metadataSizeLimit))
      .async
    // .buffer(10, OverflowStrategy.backpressure) // Buffer metadatas

    val graph = GraphDSL.create(writeStream, createMetadataStream)(Keep.none) { implicit builder ⇒ (writeFile, createMetadata) ⇒
      import GraphDSL.Implicits._

      val bytesInput = builder.add(Broadcast[ByteString](2))
      val fileInput  = builder.add(Broadcast[File](2))

      val writeMetadataChunks   = builder.add(internalStreams.preWriteAll(regionId))
      val extractMetadataSource = builder.add(Flow[(MDDisposition, FileIndexer.Result)].via(AkkaStreamUtils.extractUpstream))
      val zipSourceAndFile      = builder.add(Zip[Source[(MDDisposition, FileIndexer.Result), NotUsed], File])
      val writeMetadata = builder.add(
        Flow[(Source[(MDDisposition, FileIndexer.Result), NotUsed], File)]
          .flatMapConcat { case (metadataIn, file) ⇒
            metadataIn.flatMapConcat { case (disposition, chunkStream) ⇒
              val path    = MetadataUtils.getFilePath(file.id, disposition)
              val newFile = File.create(path, chunkStream.checksum, chunkStream.chunks)
              Source.future(regionOps.createFile(regionId, newFile))
            }
          }
          .log("metadata-files")
          .fold(Vector.empty[File])(_ :+ _)
          .recover { case _ ⇒ Vector.empty }
      )
      val zipFileAndMetadata = builder.add(Zip[File, Seq[File]])

      bytesInput ~> createMetadata ~> writeMetadataChunks ~> extractMetadataSource ~> zipSourceAndFile.in0
      bytesInput ~> writeFile ~> fileInput

      fileInput ~> zipSourceAndFile.in1
      fileInput ~> zipFileAndMetadata.in0

      zipSourceAndFile.out ~> writeMetadata ~> zipFileAndMetadata.in1

      FlowShape(bytesInput.in, zipFileAndMetadata.out)
    }

    Flow.fromGraph(graph).named("fileWithMetadataWrite")
  }

  private[this] object internalStreams {
    def preWriteFile(regionId: RegionId): Flow[Metadata, FileIndexer.Result, NotUsed] = Flow[Metadata]
      .via(StreamSerialization.serializeFramed(serialization, serializationConfig.frameLimit))
      .via(StreamCompression.compress(serializationConfig.compression))
      .via(fileStreams.writeChunkStream(regionId))
      .named("metadataPreWriteFile")

    def preWriteAll(regionId: RegionId): Flow[Metadata, (MDDisposition, FileIndexer.Result), NotUsed] = {
      Flow[Metadata]
        .groupBy(10, v ⇒ MetadataUtils.getDisposition(v.tag))
        .via(AkkaStreamUtils.extractUpstream)
        .flatMapConcat { stream ⇒
          val graph = GraphDSL.create() { implicit builder ⇒
            import GraphDSL.Implicits._
            val broadcast               = builder.add(Broadcast[Metadata](2))
            val extractDisposition      = builder.add(Flow[Metadata].map(md ⇒ MetadataUtils.getDisposition(md.tag)))
            val writeChunks             = builder.add(preWriteFile(regionId))
            val zipDispositionAndChunks = builder.add(Zip[MDDisposition, FileIndexer.Result]())

            broadcast ~> extractDisposition
            broadcast ~> writeChunks
            extractDisposition.take(1) ~> zipDispositionAndChunks.in0
            writeChunks ~> zipDispositionAndChunks.in1
            FlowShape(broadcast.in, zipDispositionAndChunks.out)
          }

          stream
            .via(graph)
            .recoverWithRetries(1, { case _ ⇒ Source.empty })
        }
        .mergeSubstreams
        .named("metadataPreWrite")
    }

    def readMetadataFile(regionId: RegionId, fileId: FileId, disposition: MDDisposition) = {
      fileStreams
        .readMostRecent(regionId, MetadataUtils.getFilePath(fileId, disposition))
        .via(StreamCompression.decompress)
        .via(StreamSerialization.deserializeFramed[Metadata](serialization, serializationConfig.frameLimit))
        .recoverWithRetries(1, { case _ ⇒ Source.empty })
    }
  }
}
