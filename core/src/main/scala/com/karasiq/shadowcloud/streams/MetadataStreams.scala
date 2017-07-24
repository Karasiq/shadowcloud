package com.karasiq.shadowcloud.streams

import java.util.UUID

import scala.concurrent.Future

import akka.NotUsed
import akka.stream.scaladsl.{Compression, Flow, Source}

import com.karasiq.shadowcloud.index.{File, Folder, Path}
import com.karasiq.shadowcloud.metadata.{Metadata, MetadataContainer}
import Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.serialization.{SerializationModule, StreamSerialization}
import com.karasiq.shadowcloud.utils.Utils

private[shadowcloud] object MetadataStreams {
  def apply(fileStreams: FileStreams, regionOps: RegionOps, serialization: SerializationModule): MetadataStreams = {
    new MetadataStreams(fileStreams, regionOps, serialization)
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

private[shadowcloud] final class MetadataStreams(fileStreams: FileStreams,
                                                 regionOps: RegionOps,
                                                 serialization: SerializationModule) {

  def keys(regionId: String): Source[File.ID, NotUsed] = {
    Source.fromFuture(regionOps.getFolder(regionId, MetadataStreams.metadataFolderPath))
      .recover { case _ ⇒ Folder(MetadataStreams.metadataFolderPath) }
      .mapConcat(_.folders.map(UUID.fromString))
  }
  
  def write(regionId: String, fileId: File.ID, disposition: MDDisposition): Flow[Metadata, File, NotUsed] = {
    def writeMetadataFile(regionId: String, path: Path) = {
      Flow[MetadataContainer]
        .via(StreamSerialization(serialization).toBytes)
        .via(Compression.gzip)
        .via(fileStreams.write(regionId, path))
    }

    Flow[Metadata]
      .fold(Vector.empty[Metadata])(_ :+ _)
      .filter(_.nonEmpty)
      .map(MetadataContainer(Some(Metadata.Tag(disposition = disposition)), fileId, _))
      .flatMapConcat { mc ⇒
        val path = MetadataStreams.getFilePath(mc.fileId, MetadataStreams.getDisposition(mc.tag))
        Source.single(mc).via(writeMetadataFile(regionId, path))
      }
  }

  def write(regionId: String, fileId: File.ID): Flow[Metadata, File, NotUsed] = {
    Flow[Metadata]
      .groupBy(10, v ⇒ MetadataStreams.getDisposition(v.tag))
      .fold(Vector.empty[Metadata])(_ :+ _)
      .flatMapConcat { values ⇒
        val disposition = MetadataStreams.getDisposition(values.head.tag)
        Source(values).via(write(regionId, fileId, disposition))
      }
      .mergeSubstreamsWithParallelism(2)
  }

  def read(regionId: String, fileId: File.ID, disposition: MDDisposition): Source[Metadata, NotUsed] = {
    def readMetadataFile(regionId: String, fileId: File.ID, disposition: MDDisposition) = {
      fileStreams.readMostRecent(regionId, MetadataStreams.getFilePath(fileId, disposition))
        .via(Compression.gunzip())
        .via(ByteStringConcat())
        .via(StreamSerialization(serialization).fromBytes[MetadataContainer])
        .recover { case _ ⇒ MetadataContainer(Some(Metadata.Tag(disposition = disposition)), fileId) }
    }

    readMetadataFile(regionId, fileId, disposition)
      .filter(mc ⇒ mc.fileId == fileId && MetadataStreams.getDisposition(mc.tag) == disposition)
      .mapConcat(_.metadata.toVector)
  }

  def delete(regionId: String, fileId: File.ID): Future[Folder] = {
    val path = MetadataStreams.getFolderPath(fileId)
    regionOps.deleteFolder(regionId, path)
  }
}
