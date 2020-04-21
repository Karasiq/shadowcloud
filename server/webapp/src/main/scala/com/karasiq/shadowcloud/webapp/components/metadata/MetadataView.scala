package com.karasiq.shadowcloud.webapp.components.metadata



import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext
import scalaTags.all._

object MetadataView {
  def thumbnail(preview: Metadata.Thumbnail): ThumbnailImage = {
    ThumbnailImage(preview)
  }

  def text(text: Metadata.Text)(implicit context: AppContext): TextView = {
    TextView(text)
  }

  def table(table: Metadata.Table)(implicit context: AppContext): TableView = {
    TableView(table)
  }

  def fileList(files: Metadata.FileList)(implicit context: AppContext): FileListView = {
    FileListView(files)
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

      case FileList(files) if files.files.nonEmpty ⇒
        this.fileList(files)

      case Text(text) ⇒
        this.text(text)

      case Table(table) ⇒
        this.table(table)

      case EmbeddedResources(resources) ⇒
        this.embeddedResources(resources)

      case _ ⇒
        Bootstrap.noContent
    }
  }
}
