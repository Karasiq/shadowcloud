package com.karasiq.shadowcloud.webapp.components.file

import com.karasiq.bootstrap.Bootstrap.default._
import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.metadata.Metadata.Tag.{Disposition ⇒ MDDisposition}
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.metadata.MetadataListView
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.FileController
import rx.Rx
import rx.async._
import scalaTags.all._

import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object FileView {
  def apply(
      file: File,
      useId: Boolean = false
  )(implicit context: AppContext, folderContext: FolderContext, fileController: FileController): FileView = {
    new FileView(file, useId)
  }
}

class FileView(file: File, useId: Boolean)(implicit context: AppContext, folderContext: FolderContext, fileController: FileController)
    extends BootstrapHtmlComponent {

  def renderTag(md: ModifierT*): TagT = {
    def renderInfo(name: String, str: String): Frag = {
      span(name + ": " + str, br)
    }

    val metadatasAvailable = context.api.listFileMetadata(folderContext.regionId, file.id).toRx(Set.empty)

    val tabs = Rx {
      val isPreviewAvailable = metadatasAvailable().contains(MDDisposition.PREVIEW)

      val preview = Some(NavigationTab(context.locale.preview, "preview", AppIcons.preview, FilePreview(folderContext.regionId, file)))
        .filter(_ ⇒ isPreviewAvailable)

      val metadata = Some(
        NavigationTab(context.locale.metadata, "metadata", AppIcons.metadata, MetadataListView(folderContext.regionId, file, metadatasAvailable))
      )
        .filter(_ ⇒ metadatasAvailable().nonEmpty)

      preview.toSeq ++ metadata ++ Seq(
        NavigationTab(context.locale.revisions, "revisions", AppIcons.revisions, FileRevisionsView(file.path)),
        NavigationTab(context.locale.availability, "availability", AppIcons.availability, FileAvailabilityView(file))
      )
    }

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
      new UniversalNavigation(tabs)
    )
  }
}
