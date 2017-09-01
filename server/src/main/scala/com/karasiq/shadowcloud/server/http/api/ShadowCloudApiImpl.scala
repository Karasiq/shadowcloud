package com.karasiq.shadowcloud.server.http.api

import scala.concurrent.Future

import akka.stream.scaladsl.Sink

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model.{FileId, Folder, Path, RegionId}

private[server] final class ShadowCloudApiImpl(sc: ShadowCloudExtension) extends ShadowCloudApi {
  import sc.implicits.{executionContext, materializer}

  def getFolder(regionId: RegionId, path: Path): Future[Folder] = {
    sc.ops.region.getFolder(regionId, path).map(_.withoutChunks)
  }

  def createFolder(regionId: RegionId, path: Path): Future[Folder] = {
    sc.ops.region.createFolder(regionId, path)
      .flatMap(_ ⇒ getFolder(regionId, path))
  }

  def deleteFolder(regionId: RegionId, path: Path): Future[Folder] = {
    sc.ops.region.deleteFolder(regionId, path)
  }

  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition): Future[Seq[Metadata]] = {
    sc.streams.metadata.read(regionId, fileId, disposition)
      .runWith(Sink.seq)
  }
}
