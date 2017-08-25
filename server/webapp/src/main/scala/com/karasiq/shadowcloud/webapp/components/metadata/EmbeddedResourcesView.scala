package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.webapp.context.AppContext

object EmbeddedResourcesView {
  def apply(resources: Metadata.EmbeddedResources)(implicit context: AppContext): EmbeddedResourcesView = {
    new EmbeddedResourcesView(resources)
  }
}

class EmbeddedResourcesView(resources: Metadata.EmbeddedResources)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val groupedResources = resources.resources
      .groupBy(_.path)
      .toSeq
      .sortBy(_._1)

    div(
      groupedResources.map { case (path, resource) ⇒
        EmbeddedResourceView(if (path.isEmpty) context.locale.unknown else path, resource.flatMap(_.metadata)).renderTag()
      },
      md
    )
  }
}

