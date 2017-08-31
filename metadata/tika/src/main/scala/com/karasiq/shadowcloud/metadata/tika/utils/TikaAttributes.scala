package com.karasiq.shadowcloud.metadata.tika.utils

import com.karasiq.shadowcloud.metadata.tika.TikaMetadata

private[tika] object TikaAttributes {
  val ResourceName = "resourceName"
  val Size = "Content-Length"
  val LastModified = "Last-Modified"

  def optional(md: TikaMetadata, name: String): Option[String] = {
    Option(md.get(name)).filter(_.nonEmpty)
  }
}
