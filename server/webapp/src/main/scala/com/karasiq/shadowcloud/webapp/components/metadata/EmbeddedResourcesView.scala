package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.{Rx, Var}

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext

object EmbeddedResourcesView {
  def apply(resources: Metadata.EmbeddedResources)(implicit context: AppContext): EmbeddedResourcesView = {
    new EmbeddedResourcesView(resources)
  }
}

class EmbeddedResourcesView(resources: Metadata.EmbeddedResources)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    def renderLine(path: String, metadatas: Seq[Metadata]): Tag = {
      val opened = Var(false)
      div(
        // Link
        a(
          href := "#",
          Rx(if (opened()) "▼" else "►"),
          Bootstrap.nbsp,
          path,
          " (",
          context.locale.entries(metadatas.length),
          ")",
          onclick := Callback.onClick(_ ⇒ opened() = !opened.now)
        ),

        // Content
        Rx[Frag](if (opened()) {
          MetadataTable(metadatas)
        } else {
          ()
        })
      )
    }

    div(
      resources.resources.groupBy(_.path).toSeq.sortBy(_._1).map { case (path, resource) ⇒
        renderLine(if (path.isEmpty) context.locale.unknown else path, resource.flatMap(_.metadata))
      },
      md
    )
  }
}

