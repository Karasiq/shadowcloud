package com.karasiq.shadowcloud.server.http.api

import akka.stream.scaladsl.Sink

import com.karasiq.shadowcloud.ShadowCloudExtension
import com.karasiq.shadowcloud.api.ShadowCloudApi
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model._
import com.karasiq.shadowcloud.model.utils.IndexScope

private[server] final class ShadowCloudApiImpl(sc: ShadowCloudExtension) extends ShadowCloudApi {
  import sc.implicits.{executionContext, materializer}

  def getFolder(regionId: RegionId, path: Path, scope: IndexScope = IndexScope.default) = {
    sc.ops.region.getFolder(regionId, path, scope).map(_.withoutChunks)
  }

  def createFolder(regionId: RegionId, path: Path) = {
    sc.ops.region.createFolder(regionId, path)
      .flatMap(_ ⇒ getFolder(regionId, path))
  }

  def deleteFolder(regionId: RegionId, path: Path) = {
    sc.ops.region.deleteFolder(regionId, path)
  }

  def getFiles(regionId: RegionId, path: Path, scope: IndexScope = IndexScope.default) = {
    sc.ops.region.getFiles(regionId, path, scope)
  }

  def getFileAvailability(regionId: RegionId, file: File) = {
    sc.ops.region.getFileAvailability(regionId, file)
  }

  def getFileMetadata(regionId: RegionId, fileId: FileId, disposition: Metadata.Tag.Disposition) = {
    sc.streams.metadata.read(regionId, fileId, disposition).runWith(Sink.seq)
  }
}
