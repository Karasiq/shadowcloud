package com.karasiq.shadowcloud.webapp.components.metadata

import scala.language.postfixOps

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext

object MetadataView {
  def preview(preview: Metadata.Preview): PreviewImage = {
    PreviewImage(preview)
  }

  def fileList(files: Metadata.ArchiveFiles): ArchiveFilesView = {
    ArchiveFilesView(files)
  }

  def text(text: Metadata.Text): Tag = {
    p(text.data)
  }

  def apply(metadata: Metadata)(implicit context: AppContext): Frag = {
    import Metadata.Value._
    metadata.value match {
      case Preview(preview) ⇒
        this.preview(preview)

      case ArchiveFiles(files) if files.files.nonEmpty ⇒
        this.fileList(files)

      case Text(text) if text.format == "text/plain" ⇒
        this.text(text)

      case _ ⇒
        ()
    }
  }
}
