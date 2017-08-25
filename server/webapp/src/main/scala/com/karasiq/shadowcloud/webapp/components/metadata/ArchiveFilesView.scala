package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.utils.MemorySize
import com.karasiq.shadowcloud.webapp.context.AppContext

object ArchiveFilesView {
  def apply(files: Metadata.ArchiveFiles)(implicit context: AppContext): ArchiveFilesView = {
    new ArchiveFilesView(files)
  }
}

class ArchiveFilesView(files: Metadata.ArchiveFiles)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val tableHeading = Seq[Modifier](
      context.locale.name,
      context.locale.size,
      context.locale.modifiedDate
    )
    val rows = files.files.map { file ⇒
      TableRow(Seq(
        file.path.mkString("/"),
        MemorySize.toString(file.size),
        context.timeFormat.timestamp(file.timestamp)
      ))
    }
    PagedTable.static(tableHeading, rows, 10)
  }
}

