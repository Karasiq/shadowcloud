package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.context.AppContext

object MetadataTable {
  def apply(metadatas: Seq[Metadata])(implicit appContext: AppContext): MetadataTable = {
    withController(metadatas, Controller.default)
  }

  def withController(metadatas: Seq[Metadata], controller: Controller): MetadataTable = {
    new MetadataTable(metadatas)(controller)
  }

  private object utils {
    import Metadata.Value._

    def metadataTagString(metadata: Metadata)(implicit context: AppContext): String = {
      metadata.tag.fold(context.locale.unknown)(tag ⇒ tag.plugin + "/" + tag.parser)
    }

    def metadataTypeString(metadata: Metadata)(implicit context: AppContext): String = metadata.value match {
      case ImageData(_) ⇒
        context.locale.metadataImageData

      case Thumbnail(thumbnail) ⇒
        context.locale.metadataThumbnail + " (" + thumbnail.format + ")"

      case Text(text) ⇒
        context.locale.metadataText + " (" + text.format + ")"

      case Table(_) ⇒
        context.locale.metadataTable

      case FileList(_) ⇒
        context.locale.metadataFileList

      case EmbeddedResources(_) ⇒
        context.locale.metadataEmbeddedResources

      case _ ⇒
        context.locale.unknown
    }

    def metadataContent(metadata: Metadata)(implicit controller: Controller): Tag = metadata.value match {
      case ImageData(imageData) ⇒
        MetadataView.imageData(imageData)

      case _ ⇒
        a(href := "#", controller.appContext.locale.show, onclick := Callback.onClick(_ ⇒ controller.show(metadata)))
    }

    def showMetadataModal(metadata: Metadata)(implicit context: AppContext): Unit = {
      Modal()
        .withTitle(context.locale.metadata)
        .withBody(MetadataView(metadata))
        .withButtons(AppComponents.modalClose())
        .show()
    }
  }

  private[metadata] sealed abstract class Controller(implicit val appContext: AppContext) {
    def show(metadata: Metadata): Unit
  }

  private[metadata] object Controller {
    def apply(fShow: Metadata ⇒ Unit)(implicit ac: AppContext): Controller = {
      new Controller {
        def show(metadata: Metadata) = fShow(metadata)
      }
    }

    def default(implicit appContext: AppContext): Controller = {
      apply(utils.showMetadataModal)
    }
  }
}

final class MetadataTable(metadatas: Seq[Metadata])(implicit controller: MetadataTable.Controller) extends BootstrapHtmlComponent {
  import controller.appContext

  import MetadataTable.utils

  def renderTag(md: ModifierT*): TagT = {
    val heading = Seq[Modifier](
      appContext.locale.metadataParser,
      appContext.locale.metadataType,
      appContext.locale.content
    )

    val rows = metadatas.map(metadata ⇒ TableRow(Seq(
      utils.metadataTagString(metadata),
      utils.metadataTypeString(metadata),
      utils.metadataContent(metadata),
    )))

    PagedTable.static(heading, rows)
  }
}

