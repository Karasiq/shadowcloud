package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Rx

import com.karasiq.shadowcloud.index.Folder
import com.karasiq.shadowcloud.model.RegionId
import com.karasiq.shadowcloud.utils.MemorySize
import com.karasiq.shadowcloud.webapp.components.file.FileDownloadLink
import com.karasiq.shadowcloud.webapp.context.AppContext
import com.karasiq.shadowcloud.webapp.utils.WebAppUtils

object FolderFileList {
  def apply(regionId: RegionId, folder: Rx[Folder])(implicit context: AppContext): FolderFileList = {
    new FolderFileList(regionId, folder)
  }
}

class FolderFileList(regionId: RegionId, folder: Rx[Folder])(implicit context: AppContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val heading = Seq[Modifier](
      context.locale.fileId,
      context.locale.name,
      context.locale.size,
      context.locale.modifiedDate
    )

    val rows = folder.map(_.files.toSeq.sortBy(_.path.name).map { file ⇒
      TableRow(Seq(
        FileDownloadLink(regionId, file)(file.id.toString),
        file.path.name,
        MemorySize.toString(file.checksum.size),
        WebAppUtils.timestampToString(file.timestamp.lastModified)
      ))
    })

    val table = PagedTable(Rx(heading), rows)
    table.renderTag(md:_*)
  }
}
