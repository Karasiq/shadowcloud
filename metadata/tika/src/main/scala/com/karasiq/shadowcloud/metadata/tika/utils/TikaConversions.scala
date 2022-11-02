package com.karasiq.shadowcloud.metadata.tika.utils

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.metadata.tika.TikaMetadata
import com.karasiq.shadowcloud.metadata.Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.utils.Utils

private[tika] object TikaConversions {
  def apply(parser: String): TikaConversions = {
    new TikaConversions(TikaUtils.PluginId, parser)
  }
}

private[tika] class TikaConversions(plugin: String, parser: String) {
  def toMetadataTable(metadata: TikaMetadata): Option[Metadata] = {
    Option(metadata)
      .filter(_.size() > 0)
      .map { metadataMap ⇒
        val values = metadataMap
          .names()
          .map(name ⇒ (name, Metadata.Table.Values(metadataMap.getValues(name).toVector)))
          .toMap
        Metadata(createTag(MDDisposition.METADATA), Metadata.Value.Table(Metadata.Table(values)))
      }
  }

  def toEmbeddedResources(metadatas: Seq[Seq[Metadata]]): Option[Metadata] = {
    val resources = metadatas.map { metadatas ⇒
      val resourcePath = {
        val resourcePaths = metadatas.iterator.flatMap(TikaUtils.extractMetadataPath).map(Path.fromString)
        if (resourcePaths.hasNext) resourcePaths.next() else Path.root
      }
      Metadata.EmbeddedResources.EmbeddedResource(resourcePath.nodes, metadatas)
    }

    val groupedResources = resources
      .filter(_.metadata.nonEmpty)
      .groupBy(_.path)
      .map { case (path, resources) ⇒
        val allMetadatas = resources.flatMap(_.metadata)
        Metadata.EmbeddedResources.EmbeddedResource(path, allMetadatas)
      }

    Some(groupedResources)
      .filter(_.nonEmpty)
      .map(resources ⇒ Metadata(createTag(MDDisposition.CONTENT), Metadata.Value.EmbeddedResources(Metadata.EmbeddedResources(resources.toVector))))
  }

  def toArchiveTables(subMetadatas: Seq[Metadata], previewSize: Int): Seq[Metadata] = {
    val archiveFiles = for {
      md   ← subMetadatas if md.value.isTable
      path ← TikaAttributes.optional(md, TikaAttributes.ResourceName).map(ps ⇒ Path.fromString(ps).nodes)
      size ← TikaAttributes.optional(md, TikaAttributes.Size).map(_.toLong)
      timestamp = TikaAttributes.optional(md, TikaAttributes.LastModified).fold(0L)(TikaUtils.parseTimeString)
    } yield Metadata.FileList.File(path, size, timestamp)

    Some(archiveFiles)
      .filter(_.nonEmpty)
      .toSeq
      .flatMap { files ⇒
        val preview = Metadata(createTag(MDDisposition.PREVIEW), Metadata.Value.FileList(Metadata.FileList(files.take(previewSize))))
        if (files.length > previewSize) {
          val full = Metadata(createTag(MDDisposition.CONTENT), Metadata.Value.FileList(Metadata.FileList(files)))
          Seq(preview, full)
        } else {
          Seq(preview)
        }
      }
  }

  def toTextPreviews(metadatas: Seq[Metadata], maxPreviews: Int, maxLength: Int): Seq[Metadata] = {
    metadatas.iterator
      .filter(_.tag.exists(_.disposition == MDDisposition.CONTENT))
      .flatMap(_.value.text)
      .filter(_.format == TikaFormats.Text)
      .map(text ⇒ Utils.takeWords(text.data, maxLength))
      .filter(_.nonEmpty)
      .take(maxPreviews)
      .toVector
      .sortBy(_.length)(Ordering[Int].reverse)
      .map(result ⇒ Metadata(createTag(MDDisposition.PREVIEW), Metadata.Value.Text(Metadata.Text(TikaFormats.Text, result))))
  }

  def toImageData(metadataTable: Metadata): Option[Metadata] = {
    require(metadataTable.value.isTable, s"Not a table: $metadataTable")
    for {
      width  ← TikaAttributes.optional(metadataTable, TikaAttributes.ImageWidth)
      height ← TikaAttributes.optional(metadataTable, TikaAttributes.ImageHeight)
    } yield Metadata(createTag(MDDisposition.METADATA), Metadata.Value.ImageData(Metadata.ImageData(width.toInt, height.toInt)))
  }

  def toDescription(metadataTable: Metadata): Option[Metadata] = {
    require(metadataTable.value.isTable, s"Not a table: $metadataTable")

    val title       = TikaAttributes.optional(metadataTable, TikaAttributes.Title)
    val description = TikaAttributes.optional(metadataTable, TikaAttributes.Description)

    Some((title.toSeq ++ description).mkString("\r\n\r\n").trim)
      .filter(_.nonEmpty)
      .map(text ⇒ Metadata(createTag(MDDisposition.PREVIEW), Metadata.Value.Text(Metadata.Text("text/plain", text))))
  }

  private[this] def createTag(disposition: MDDisposition): Option[Metadata.Tag] = {
    Some(Metadata.Tag(plugin, parser, disposition))
  }
}
