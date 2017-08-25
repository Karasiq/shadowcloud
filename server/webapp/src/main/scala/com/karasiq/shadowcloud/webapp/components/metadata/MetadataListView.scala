package com.karasiq.shadowcloud.webapp.components.metadata

import scala.language.postfixOps
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

import rx.{Rx, Var}
import rx.async._

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.index.File
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.webapp.context.AppContext

object MetadataListView {
  def apply(regionId: RegionId, file: File)(implicit context: AppContext): MetadataListView = {
    new MetadataListView(regionId, file)
  }

  private def dispositionToString(disposition: Metadata.Tag.Disposition)(implicit context: AppContext): String = {
    import Metadata.Tag.Disposition._

    disposition match {
      case PREVIEW ⇒
        context.locale.preview

      case METADATA ⇒
        context.locale.metadata

      case CONTENT ⇒
        context.locale.content

      case _ ⇒
        context.locale.unknown
    }
  }
}

class MetadataListView(regionId: RegionId, file: File)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    def renderDisposition(disposition: Metadata.Tag.Disposition): Tag = {
      val opened = Var(false)
      div(
        a(
          href := "#",
          Rx(if (opened()) "▼" else "►"),
          Bootstrap.nbsp,
          MetadataListView.dispositionToString(disposition),
          onclick := Callback.onClick(_ ⇒ opened() = !opened.now)
        ),
        Rx[Frag](if (opened()) {
          val metadata = context.api.getFileMetadata(regionId, file.id, disposition).toRx(Nil)
          div(metadata.map(MetadataTable(_): Frag))
        } else {
          ()
        })
      )
    }

    div(
      p(renderDisposition(Metadata.Tag.Disposition.PREVIEW)),
      p(renderDisposition(Metadata.Tag.Disposition.METADATA)),
      p(renderDisposition(Metadata.Tag.Disposition.CONTENT))
    )
  }
}

