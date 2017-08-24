package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.utils.Blobs

object PreviewImage {
  def apply(preview: Metadata.Preview): PreviewImage = {
    new PreviewImage(preview)
  }
}

class PreviewImage(preview: Metadata.Preview) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val blob = Blobs.fromBytes(preview.data.toArray)
    val imageUrl = Blobs.getUrl(blob)
    img(src := imageUrl, alt := "Preview image")
  }
}

