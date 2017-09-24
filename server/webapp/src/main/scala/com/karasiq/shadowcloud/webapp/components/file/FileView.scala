package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.metadata.MetadataListView
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

object FileView {
  def apply(file: File, useId: Boolean = false)(implicit context: AppContext, folderContext: FolderContext): FileView = {
    new FileView(file, useId)
  }
}

class FileView(file: File, useId: Boolean)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    def renderInfo(name: String, str: String): Frag = {
      span(name + ": " + str, br)
    }

    val tabs = Seq(
      NavigationTab(context.locale.preview, "preview", AppIcons.preview, FilePreview(folderContext.regionId, file)),
      NavigationTab(context.locale.metadata, "metadata", AppIcons.metadata, MetadataListView(folderContext.regionId, file)),
      NavigationTab(context.locale.revisions, "revisions", AppIcons.revisions, FileRevisionsView(file.path)),
      NavigationTab(context.locale.availability, "availability", AppIcons.availability, FileAvailabilityView(file))
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
      hr,
      FileActions(file, useId),
      hr,
      Navigation.tabs(tabs:_*)
    )
  }
}

