package com.karasiq.shadowcloud.webapp.components.folder

import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

private[folder] object RichFileTable {
  def apply(files: Rx[Seq[File]], selectedFile: Var[Option[File]])
           (implicit context: AppContext, fc: FolderContext): RichFileTable = {
    new RichFileTable(files, selectedFile)
  }
}

private[folder] class RichFileTable(files: Rx[Seq[File]], selectedFile: Var[Option[File]])
                                   (implicit context: AppContext, fc: FolderContext) extends BootstrapHtmlComponent {

  private[this] val rowsPerPage = 10
  private[this] val pagesRx = files.map(_.length / rowsPerPage + 1)
  val pageSelector = PageSelector(pagesRx)

  def renderTag(md: ModifierT*): TagT = {
    val currentFiles = Rx {
      val data = files().sortBy(_.path.name)
      val page = pageSelector.currentPage() min pagesRx()
      data.slice(rowsPerPage * (page - 1), rowsPerPage * (page - 1) + rowsPerPage)
    }

    GridSystem.containerFluid(
      GridSystem.mkRow(textAlign.center, pageSelector, pagesRx.map(_ <= 1).reactiveHide),
      Rx(GridSystem.mkRow(currentFiles().map(FileListItem(_, selectedFile))))
    )
  }
}

