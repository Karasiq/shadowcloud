package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.components.common.AppComponents
import com.karasiq.shadowcloud.webapp.context.AppContext

object EmbeddedResourceView {
  def apply(path: String, metadatas: Seq[Metadata])(implicit context: AppContext): EmbeddedResourceView = {
    new EmbeddedResourceView(path, metadatas)
  }
}

class EmbeddedResourceView(path: String, metadatas: Seq[Metadata])(implicit context: AppContext) extends BootstrapHtmlComponent {
  val opened = Var(false)
  val selectedMetadata = Var(None: Option[Metadata])

  def renderTag(md: ModifierT*): TagT = {
    val returnToTableLink = AppComponents.iconLink(context.locale.close, "close".faFwIcon,
      Bootstrap.textStyle.danger, fontWeight.bold, onclick := Callback.onClick(_ ⇒ selectedMetadata() = None))

    div(
      // Link
      AppComponents.dropDownLink(s"$path (${context.locale.entries(metadatas.length)})", opened),

      // Content
      Rx[Frag] {
        if (opened()) {
          selectedMetadata() match {
            case Some(metadata) ⇒
              div(returnToTableLink, MetadataView(metadata))

            case None ⇒
              MetadataTable.withController(metadatas, MetadataTable.Controller(selected ⇒ selectedMetadata() = Some(selected)))
          }
        } else {
          ()
        }
      }
    )
  }
}
