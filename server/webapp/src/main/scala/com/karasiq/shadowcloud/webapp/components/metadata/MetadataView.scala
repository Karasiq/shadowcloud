package com.karasiq.shadowcloud.webapp.components.metadata

import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext

object MetadataView {
  def thumbnail(preview: Metadata.Thumbnail): ThumbnailImage = {
    ThumbnailImage(preview)
  }

  def text(text: Metadata.Text): Tag = {
    p(text.data)
  }

  def table(table: Metadata.Table)(implicit context: AppContext): TableView = {
    TableView(table)
  }

  def archiveFiles(files: Metadata.ArchiveFiles)(implicit context: AppContext): ArchiveFilesView = {
    ArchiveFilesView(files)
  }

  def embeddedResources(embeddedResources: Metadata.EmbeddedResources)(implicit context: AppContext): EmbeddedResourcesView = {
    EmbeddedResourcesView(embeddedResources)
  }

  def imageData(imageData: Metadata.ImageData): Tag = {
    span(imageData.width, "x", imageData.height)
  }

  def apply(metadata: Metadata)(implicit context: AppContext): Frag = {
    import Metadata.Value._
    metadata.value match {
      case Thumbnail(preview) ⇒
        this.thumbnail(preview)

      case ArchiveFiles(files) if files.files.nonEmpty ⇒
        this.archiveFiles(files)

      case Text(text) ⇒
        this.text(text)

      case Table(table) ⇒
        this.table(table)

      case EmbeddedResources(resources) ⇒
        this.embeddedResources(resources)

      case _ ⇒
        ()
    }
  }
}
