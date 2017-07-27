package com.karasiq.shadowcloud.streams

import java.util.UUID

import scala.concurrent.Future

import akka.NotUsed
import akka.stream._
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Source, Zip}
import akka.util.ByteString

import com.karasiq.shadowcloud.config.MetadataConfig
import com.karasiq.shadowcloud.index.{File, Folder, Path}
import com.karasiq.shadowcloud.metadata.Metadata
import Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.providers.MetadataModuleRegistry
import com.karasiq.shadowcloud.serialization.{SerializationModule, StreamSerialization}
import com.karasiq.shadowcloud.streams.metadata.MimeDetectorStream
import com.karasiq.shadowcloud.streams.utils.ByteStringLimit
import com.karasiq.shadowcloud.utils.Utils

private[shadowcloud] object MetadataStreams {
  def apply(config: MetadataConfig, modules: MetadataModuleRegistry,
            fileStreams: FileStreams, regionOps: RegionOps,
            serialization: SerializationModule): MetadataStreams = {
    new MetadataStreams(config, modules, fileStreams, regionOps, serialization)
  }

  private val metadataFolderPath = Utils.internalFolderPath / "metadata"

  private def getFolderPath(fileId: File.ID): Path = {
    metadataFolderPath / fileId.toString.toLowerCase
  }

  private def getFilePath(fileId: File.ID, disposition: MDDisposition): Path = {
    getFolderPath(fileId) / disposition.toString().toLowerCase
  }

  private def getDisposition(tag: Option[Metadata.Tag]): MDDisposition = {
    tag.fold(MDDisposition.METADATA: MDDisposition)(_.disposition)
  }
}

private[shadowcloud] final class MetadataStreams(config: MetadataConfig,
                                                 modules: MetadataModuleRegistry,
                                                 fileStreams: FileStreams,
                                                 regionOps: RegionOps,
                                                 serialization: SerializationModule) {

  def keys(regionId: String): Source[File.ID, NotUsed] = {
    Source.fromFuture(regionOps.getFolder(regionId, MetadataStreams.metadataFolderPath))
      .recover { case _ ⇒ Folder(MetadataStreams.metadataFolderPath) }
      .mapConcat(_.folders.map(UUID.fromString))
      .named("metadataFileKeys")
  }
  
  def write(regionId: String, fileId: File.ID, disposition: MDDisposition): Flow[Metadata, File, NotUsed] = {
    def writeMetadataFile(regionId: String, path: Path) = {
      Flow[Metadata]
        .via(StreamSerialization.serializeFramedGzip(serialization, config.metadataFrameLimit))
        .via(fileStreams.write(regionId, path))
    }

    Flow[Metadata]
      .via(writeMetadataFile(regionId, MetadataStreams.getFilePath(fileId, disposition)))
      .named("metadataWrite")
  }

  def write(regionId: String, fileId: File.ID): Flow[Metadata, File, NotUsed] = {
    Flow[Metadata]
      .groupBy(10, v ⇒ MetadataStreams.getDisposition(v.tag))
      .prefixAndTail(1)
      .flatMapConcat { case (head, stream) ⇒
        val disposition = MetadataStreams.getDisposition(head.head.tag)
        Source(head)
          .concat(stream)
          .via(write(regionId, fileId, disposition))
      }
      .mergeSubstreams
      .named("metadataAutoWrite")
  }

  def list(regionId: String, fileId: File.ID): Future[Folder] = {
    val path = MetadataStreams.getFolderPath(fileId)
    regionOps.getFolder(regionId, path)
  }

  def read(regionId: String, fileId: File.ID, disposition: MDDisposition): Source[Metadata, NotUsed] = {
    def readMetadataFile(regionId: String, fileId: File.ID, disposition: MDDisposition) = {
      fileStreams.readMostRecent(regionId, MetadataStreams.getFilePath(fileId, disposition))
        .via(StreamSerialization.deserializeFramedGzip[Metadata](serialization, config.metadataFrameLimit))
        .recoverWithRetries(1, { case _ ⇒ Source.empty })
    }

    readMetadataFile(regionId, fileId, disposition).named("metadataRead")
  }

  def delete(regionId: String, fileId: File.ID): Future[Folder] = {
    val path = MetadataStreams.getFolderPath(fileId)
    regionOps.deleteFolder(regionId, path)
  }

  def create(fileName: String, sizeLimit: Long = config.fileSizeLimit): Flow[ByteString, Metadata, NotUsed] = {
    val graph = GraphDSL.create() { implicit builder ⇒
      import GraphDSL.Implicits._
      val bytesInput = builder.add(Broadcast[ByteString](2))

      val extractStream = builder.add(Flow[ByteString].async.prefixAndTail(0).map(_._2))
      val getContentType = builder.add(MimeDetectorStream(modules, fileName, config.mimeProbeSize))

      val zipStreamAndMime = builder.add(Zip[Source[ByteString, NotUsed], String])

      val parseMetadata = builder.add(Flow[(Source[ByteString, NotUsed], String)]
        .flatMapConcat { case (bytes, contentType) ⇒
          if (modules.canParse(fileName, contentType)) {
            bytes.via(modules.parseMetadata(fileName, contentType))
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

    ByteStringLimit(sizeLimit)
      .via(graph)
      .recoverWithRetries(1, { case _ ⇒ Source.empty })
      .named("metadataCreate")
  }

  def writeFileAndMetadata(regionId: String, path: Path,
                           metadataSizeLimit: Long = config.fileSizeLimit): Flow[ByteString, (File, Seq[File]), NotUsed] = {
    // Writes the chunk stream before actual file path is known
    val preWrite: Flow[Metadata, (MDDisposition, FileIndexer.Result), NotUsed] = {
      val preWriteFile = Flow[Metadata]
        .via(StreamSerialization.serializeFramedGzip(serialization, config.metadataFrameLimit))
        .via(fileStreams.writeChunkStream(regionId))
        .named("metadataPreWriteFile")

      Flow[Metadata]
        .groupBy(10, v ⇒ MetadataStreams.getDisposition(v.tag))
        .prefixAndTail(1)
        .flatMapConcat { case (head, stream) ⇒
          val disposition = MetadataStreams.getDisposition(head.head.tag)
          Source(head)
            .concat(stream)
            .via(preWriteFile)
            .map((disposition, _))
            .recoverWithRetries(1, { case _ ⇒ Source.empty })
        }
        .mergeSubstreams
        .named("metadataPreWrite")
    }

    val writeStream = fileStreams.write(regionId, path)
    val createMetadataStream = create(path.name, metadataSizeLimit).buffer(5, OverflowStrategy.backpressure)

    val graph = GraphDSL.create(writeStream, createMetadataStream)(Keep.none) { implicit builder ⇒ (writeFile, createMetadata) ⇒
      import GraphDSL.Implicits._

      val bytesInput = builder.add(Broadcast[ByteString](2))
      val fileInput = builder.add(Broadcast[File](2))

      val writeMetadataChunks = builder.add(preWrite)
      val extractMetadataSource = builder.add(Flow[(MDDisposition, FileIndexer.Result)].prefixAndTail(0).map(_._2))
      val zipSourceAndFile = builder.add(Zip[Source[(MDDisposition, FileIndexer.Result), NotUsed], File])
      val writeMetadata = builder.add(Flow[(Source[(MDDisposition, FileIndexer.Result), NotUsed], File)]
        .flatMapConcat { case (metadataIn, file) ⇒
          metadataIn.flatMapConcat { case (disposition, chunkStream) ⇒
            val path = MetadataStreams.getFilePath(file.id, disposition)
            val newFile = File.create(path, chunkStream.checksum, chunkStream.chunks)
            Source.fromFuture(regionOps.createFile(regionId, newFile))
          }
        }
        .recoverWithRetries(1, { case _ ⇒ Source.empty })
        .fold(Vector.empty[File])(_ :+ _)
      )
      val zipFileAndMetadata = builder.add(Zip[File, Seq[File]])

      bytesInput ~> writeFile
      bytesInput ~> createMetadata ~> writeMetadataChunks ~> extractMetadataSource ~> zipSourceAndFile.in0

      writeFile ~> fileInput
      fileInput ~> zipSourceAndFile.in1
      fileInput ~> zipFileAndMetadata.in0

      zipSourceAndFile.out ~> writeMetadata ~> zipFileAndMetadata.in1

      FlowShape(bytesInput.in, zipFileAndMetadata.out)
    }

    Flow.fromGraph(graph).named("fileWithMetadataWrite")
  }
}
