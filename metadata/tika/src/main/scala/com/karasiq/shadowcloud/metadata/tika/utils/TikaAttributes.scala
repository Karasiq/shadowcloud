package com.karasiq.shadowcloud.metadata.tika.utils

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.tika.TikaMetadata

private[tika] object TikaAttributes {
  val ResourceName = "resourceName"
  val Size = "Content-Length"
  val LastModified = "Last-Modified"

  val ImageWidth = "tiff:ImageWidth"
  val ImageHeight = "tiff:ImageLength"

  val Title = "dc:title"
  val Description = "dc:description"

  def optional(md: TikaMetadata, name: String): Option[String] = {
    Option(md.get(name)).filter(_.nonEmpty)
  }

  def optional(md: Metadata, name: String): Option[String] = {
    md.value.table
      .flatMap(_.values.get(name))
      .flatMap(_.values.headOption)
  }
}
