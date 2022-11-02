package com.karasiq.shadowcloud.webapp.components.metadata

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.metadata.Metadata
import com.karasiq.shadowcloud.model.Path
import com.karasiq.shadowcloud.webapp.context.AppContext

object FileListView {
  def apply(files: Metadata.FileList)(implicit context: AppContext): FileListView = {
    new FileListView(files)
  }
}

class FileListView(files: Metadata.FileList)(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val tableHeading = Seq[Modifier](
      context.locale.name,
      context.locale.size,
      context.locale.modifiedDate
    )
    val rows = files.files.map { file ⇒
      TableRow(
        Seq(
          file.path.mkString(Path.Delimiter),
          MemorySize.toString(file.size),
          context.timeFormat.timestamp(file.timestamp)
        )
      )
    }
    PagedTable.static(tableHeading, rows, 10).renderTag(md: _*)
  }
}
