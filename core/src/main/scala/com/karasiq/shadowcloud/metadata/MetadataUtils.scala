package com.karasiq.shadowcloud.metadata

import java.util.UUID

import scala.util.Try

import com.karasiq.shadowcloud.index.{File, FolderIndex, Path}
import Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.utils.Utils

private[shadowcloud] object MetadataUtils {
  val metadataRoot = Utils.internalFolderPath / "metadata"

  def getFolderPath(fileId: File.ID): Path = {
    metadataRoot / fileId.toString.toLowerCase
  }

  def getFilePath(fileId: File.ID, disposition: MDDisposition): Path = {
    getFolderPath(fileId) / disposition.toString().toLowerCase
  }

  def getDisposition(tag: Option[Metadata.Tag]): MDDisposition = {
    tag.fold(MDDisposition.METADATA: MDDisposition)(_.disposition)
  }

  def expiredFileIds(index: FolderIndex): Set[File.ID] = {
    val metadataFolders = index.get(metadataRoot)
      .map(_.folders)
      .getOrElse(Set.empty)

    val metadataFileIds = metadataFolders
      .flatMap(name ⇒ Try(UUID.fromString(name)).toOption)

    val actualFileIds = index.folders
      .flatMap(_._2.files)
      .map(_.id)
      .toSet

    metadataFileIds.diff(actualFileIds)
  }
}
