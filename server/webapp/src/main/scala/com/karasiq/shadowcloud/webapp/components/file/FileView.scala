package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.{File, RegionId}
import com.karasiq.shadowcloud.utils.MemorySize
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.metadata.MetadataListView
import com.karasiq.shadowcloud.webapp.context.AppContext

object FileView {
  def apply(regionId: RegionId, file: File)(implicit context: AppContext): FileView = {
    new FileView(regionId, file)
  }
}

class FileView(regionId: RegionId, file: File)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    def renderInfo(name: String, str: String): Frag = {
      span(name + ": " + str, br)
    }

    val tabs = Seq(
      NavigationTab(context.locale.preview, "preview", AppIcons.preview, FilePreview(regionId, file)),
      NavigationTab(context.locale.metadata, "metadata", AppIcons.metadata, MetadataListView(regionId, file))
    )

    div(
      GridSystem.mkRow(h4(file.path.name)),
      hr,
      Bootstrap.well(
        renderInfo(context.locale.fileId, file.id.toString),
        renderInfo(context.locale.revision, file.revision.toString),
        renderInfo(context.locale.createdDate, context.timeFormat.timestamp(file.timestamp.created)),
        renderInfo(context.locale.modifiedDate, context.timeFormat.timestamp(file.timestamp.lastModified)),
        renderInfo(context.locale.size, MemorySize.toString(file.checksum.size))
      ),
      Navigation.tabs(tabs:_*)
    )
  }
}

