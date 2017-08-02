package com.karasiq.shadowcloud.webapp.components

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import rx.Rx

import com.karasiq.shadowcloud.index.Folder
import com.karasiq.shadowcloud.utils.MemorySize
import com.karasiq.shadowcloud.webapp.components.file.FileDownloadLink

object FolderView {
  def apply(regionId: String, folder: Rx[Folder]): FolderView = {
    new FolderView(regionId, folder)
  }
}

class FolderView(regionId: String, folder: Rx[Folder]) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val rows = folder.map(_.files.toSeq.sortBy(_.path.name).map { file ⇒
      TableRow(Seq(
        FileDownloadLink(regionId, file)(file.id.toString),
        file.path.name,
        MemorySize.toString(file.checksum.size),
      ))
    })
    val table = PagedTable(Rx(Seq("UUID", "Name", "Size")), rows)
    table.renderTag(md:_*)
  }
}
