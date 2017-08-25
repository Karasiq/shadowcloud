package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext

object MetadataTable {
  def apply(metadatas: Seq[Metadata])(implicit context: AppContext): MetadataTable = {
    new MetadataTable(metadatas)
  }

  import Metadata.Value._
  private def metadataTypeString(metadata: Metadata)(implicit context: AppContext): String = metadata.value match {
    case ImageData(_) ⇒
      context.locale.metadataImageData

    case Thumbnail(thumbnail) ⇒
      context.locale.metadataThumbnail + " (" + thumbnail.format + ")"

    case Text(text) ⇒
      context.locale.metadataText + " (" + text.format + ")"

    case Table(_) ⇒
      context.locale.metadataTable

    case ArchiveFiles(_) ⇒
      context.locale.metadataArchiveFiles

    case EmbeddedResources(_) ⇒
      context.locale.metadataEmbeddedResources

    case _ ⇒
      context.locale.unknown
  }

  private def metadataContent(metadata: Metadata)(implicit context: AppContext): Tag = metadata.value match {
    case ImageData(imageData) ⇒
      MetadataView.imageData(imageData)

    case _ ⇒
      a(href := "#", context.locale.show, onclick := Callback.onClick { _ ⇒
        Modal()
          .withTitle(context.locale.metadata)
          .withBody(MetadataView(metadata))
          .withButtons(Modal.closeButton(context.locale.close))
          .show()
      })
  }
}

class MetadataTable(metadatas: Seq[Metadata])(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val heading = Seq[Modifier](
      context.locale.metadataParser,
      context.locale.metadataType,
      context.locale.content
    )

    val rows = metadatas.map { metadata ⇒
      val parser = metadata.tag.fold(context.locale.unknown)(tag ⇒ tag.plugin + "/" + tag.parser)
      TableRow(Seq(
        parser,
        MetadataTable.metadataTypeString(metadata),
        MetadataTable.metadataContent(metadata),
      ))
    }

    PagedTable.static(heading, rows)
  }
}

