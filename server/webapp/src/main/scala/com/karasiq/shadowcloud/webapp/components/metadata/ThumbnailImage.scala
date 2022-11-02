package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.utils.Blobs

object ThumbnailImage {
  def apply(thumbnail: Metadata.Thumbnail): ThumbnailImage = {
    new ThumbnailImage(thumbnail)
  }
}

class ThumbnailImage(thumbnail: Metadata.Thumbnail) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val blob     = Blobs.fromBytes(thumbnail.data.toArray)
    val imageUrl = Blobs.getUrl(blob)
    img(src := imageUrl, alt := "Preview image")
  }
}
