package com.karasiq.shadowcloud.webapp.components

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.index.Folder

object FolderView {
  def apply(folder: Folder): FolderView = {
    new FolderView(folder)
  }
}

class FolderView(folder: Folder) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    PagedTable.static(Seq("Name", "Size"), folder.files.toSeq.sortBy(_.path.name).map { file ⇒
      TableRow(Seq(file.path.name, file.checksum.size))
    })
  }
}
