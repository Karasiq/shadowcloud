package com.karasiq.shadowcloud.streams.metadata

import java.util.UUID

import scala.concurrent.Future

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Source, Zip}
import akka.util.ByteString

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.compression.StreamCompression
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataUtils}
import Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.serialization.StreamSerialization
import com.karasiq.shadowcloud.streams.file.FileIndexer
import com.karasiq.shadowcloud.streams.utils.ByteStreams
import com.karasiq.shadowcloud.utils.AkkaStreamUtils

private[shadowcloud] object MetadataStreams {
  def apply(sc: ShadowCloudExtension): MetadataStreams = {
    new MetadataStreams(sc)
  }
}

private[shadowcloud] final class MetadataStreams(sc: ShadowCloudExtension) {
  def keys(regionId: RegionId): Source[FileId, NotUsed] = {
    Source.fromFuture(sc.ops.region.getFolder(regionId, MetadataUtils.MetadataFolder))
      .recover { case _ ⇒ Folder(MetadataUtils.MetadataFolder) }
      .mapConcat(_.folders.map(UUID.fromString))
      .named("metadataFileKeys")
  }
  
  def write(regionId: RegionId, fileId: FileId, disposition: MDDisposition): Flow[Metadata, File, NotUsed] = {
    internalStreams.preWriteFile(regionId)
      .map((regionId, MetadataUtils.getFilePath(fileId, disposition), _))
      .via(sc.streams.region.createFile)
      .named("metadataWrite")
  }

  def writeAll(regionId: RegionId, fileId: FileId): Flow[Metadata, File, NotUsed] = {
    internalStreams.preWriteAll(regionId)
      .map { case (disposition, result) ⇒ (regionId, MetadataUtils.getFilePath(fileId, disposition), result) }
      .via(sc.streams.region.createFile)
      .named("metadataWriteAll")
  }

  def list(regionId: RegionId, fileId: FileId): Future[Folder] = {
    val path = MetadataUtils.getFolderPath(fileId)
    sc.ops.region.getFolder(regionId, path)
  }

  def read(regionId: RegionId, fileId: FileId, disposition: MDDisposition): Source[Metadata, NotUsed] = {
    internalStreams
      .readMetadataFile(regionId, fileId, disposition)
      .named("metadataRead")
  }

  def delete(regionId: RegionId, fileId: FileId): Future[Folder] = {
    val path = MetadataUtils.getFolderPath(fileId)
    sc.ops.region.deleteFolder(regionId, path)
  }

  def create(fileName: String, sizeLimit: Long = sc.config.metadata.fileSizeLimit): Flow[ByteString, Metadata, NotUsed] = {
    val graph = GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._

      val bytesInput = builder.add(Broadcast[ByteString](2))
      val extractStream = builder.add(Flow[ByteString].async.prefixAndTail(0).map(_._2))
      val getContentType = builder.add(Flow[ByteString].async.via(MimeDetectorStream(sc.modules.metadata, fileName, sc.config.metadata.mimeProbeSize)))

      val zipStreamAndMime = builder.add(Zip[Source[ByteString, NotUsed], String])

      val parseMetadata = builder.add(Flow[(Source[ByteString, NotUsed], String)]
        .flatMapConcat { case (bytes, contentType) ⇒
          if (sc.modules.metadata.canParse(fileName, contentType)) {
            bytes.via(sc.modules.metadata.parseMetadata(fileName, contentType))
          } else {
            Source.empty
          }
        }
      )

      bytesInput ~> extractStream
      bytesInput ~> getContentType
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

  def writeFileAndMetadata(regionId: RegionId, path: Path,
                           metadataSizeLimit: Long = sc.config.metadata.fileSizeLimit
                          ): Flow[ByteString, (File, Seq[File]), NotUsed] = {
    // Writes the chunk stream before actual file path is known
    val writeStream = Flow[ByteString]
      .async
      .via(sc.streams.file.write(regionId, path))

    val createMetadataStream = Flow[ByteString]
      .async // .buffer(5, OverflowStrategy.backpressure) // Buffer byte chunks
      .via(create(path.name, metadataSizeLimit))
      .buffer(10, OverflowStrategy.backpressure) // Buffer metadatas

    val graph = GraphDSL.create(writeStream, createMetadataStream)(Keep.none) { implicit builder ⇒ (writeFile, createMetadata) ⇒
      import GraphDSL.Implicits._

      val bytesInput = builder.add(Broadcast[ByteString](2))
      val fileInput = builder.add(Broadcast[File](2))

      val writeMetadataChunks = builder.add(internalStreams.preWriteAll(regionId))
      val extractMetadataSource = builder.add(Flow[(MDDisposition, FileIndexer.Result)].via(AkkaStreamUtils.extractUpstream))
      val zipSourceAndFile = builder.add(Zip[Source[(MDDisposition, FileIndexer.Result), NotUsed], File])
      val writeMetadata = builder.add(Flow[(Source[(MDDisposition, FileIndexer.Result), NotUsed], File)]
        .flatMapConcat { case (metadataIn, file) ⇒
          metadataIn.flatMapConcat { case (disposition, chunkStream) ⇒
            val path = MetadataUtils.getFilePath(file.id, disposition)
            val newFile = File.create(path, chunkStream.checksum, chunkStream.chunks)
            Source.fromFuture(sc.ops.region.createFile(regionId, newFile))
          }
        }
        .log("metadata-files")
        .fold(Vector.empty[File])(_ :+ _)
        .recover { case _ ⇒ Vector.empty }
      )
      val zipFileAndMetadata = builder.add(Zip[File, Seq[File]])

      bytesInput ~> writeFile ~> fileInput
      bytesInput ~> createMetadata ~> writeMetadataChunks ~> extractMetadataSource ~> zipSourceAndFile.in0

      fileInput ~> zipSourceAndFile.in1
      fileInput ~> zipFileAndMetadata.in0

      zipSourceAndFile.out ~> writeMetadata ~> zipFileAndMetadata.in1

      FlowShape(bytesInput.in, zipFileAndMetadata.out)
    }

    Flow.fromGraph(graph).named("fileWithMetadataWrite")
  }

  private[this] object internalStreams {
    def preWriteFile(regionId: RegionId): Flow[Metadata, FileIndexer.Result, NotUsed] = Flow[Metadata]
      .via(StreamSerialization.serializeFramed(sc.serialization, sc.config.serialization.frameLimit))
      .via(StreamCompression.compress(sc.config.serialization.compression))
      .via(sc.streams.file.writeChunkStream(regionId))
      .named("metadataPreWriteFile")

    def preWriteAll(regionId: RegionId): Flow[Metadata, (MDDisposition, FileIndexer.Result), NotUsed] = {
      Flow[Metadata]
        .groupBy(10, v ⇒ MetadataUtils.getDisposition(v.tag))
        .prefixAndTail(1)
        .flatMapConcat { case (head, stream) ⇒
          val disposition = MetadataUtils.getDisposition(head.head.tag)
          Source(head)
            .concat(stream)
            .via(preWriteFile(regionId))
            .map((disposition, _))
            .recoverWithRetries(1, { case _ ⇒ Source.empty })
        }
        .mergeSubstreams
        .named("metadataPreWrite")
    }

    def readMetadataFile(regionId: RegionId, fileId: FileId, disposition: MDDisposition) = {
      sc.streams.file.readMostRecent(regionId, MetadataUtils.getFilePath(fileId, disposition))
        .via(StreamCompression.decompress)
        .via(StreamSerialization.deserializeFramed[Metadata](sc.serialization, sc.config.serialization.frameLimit))
        .recoverWithRetries(1, { case _ ⇒ Source.empty })
    }
  }
}
