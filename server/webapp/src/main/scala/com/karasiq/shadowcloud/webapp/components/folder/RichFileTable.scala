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

  private[this] lazy val sortedFiles = Rx {
    val allFiles = files()
    val filtered = {
      val filterStr = filter()
      if (filterStr.isEmpty) allFiles
      else allFiles.filter(_.toString.toLowerCase.contains(filterStr.toLowerCase))
    }
    val sorted = sortTypes(sortType()) match {
      case s if s == context.locale.name ⇒ filtered.sortBy(_.path.name)
      case s if s == context.locale.size ⇒ filtered.sortBy(_.checksum.size)
      case s if s == context.locale.modifiedDate ⇒ filtered.sortBy(_.timestamp.lastModified)
      case _ ⇒ filtered
    }
    if (sortDesc()) sorted.reverse else sorted
  }

  private[this] val rowsPerPage = 10
  private[this] lazy val pagesRx = sortedFiles.map(_.length / rowsPerPage + 1)
  private[this] val sortTypes = Seq(context.locale.name, context.locale.size, context.locale.modifiedDate)
  private[this] val sortType = Var(0)
  private[this] val sortDesc = Var(false)
  private[this] val filter = Var("")

  lazy val pageSelector = PageSelector(pagesRx)

  def renderTag(md: ModifierT*): TagT = {
    val currentPageFiles = Rx {
      val page = pageSelector.currentPage() min pagesRx()
      sortedFiles().slice(rowsPerPage * (page - 1), rowsPerPage * (page - 1) + rowsPerPage)
    }

    val filterField = Form(FormInput.text("", filter.reactiveInput))

    val sortLine = Rx {
      span(
        Icon(if (sortDesc()) "triangle-bottom" else "triangle-top"),
        b(sortTypes(sortType())),
        cursor.pointer,
        onclick := Callback.onClick(_ ⇒ changeSortType())
      )
    }

    div(
      div(filterField, files.map(_.isEmpty).reactiveHide),
      // hr(files.map(_.isEmpty).reactiveHide),
      div(textAlign.center, pageSelector, pagesRx.map(_ <= 1).reactiveHide),
      div(sortLine, sortedFiles.map(_.length <= 1).reactiveHide),
      // hr(files.map(_.isEmpty).reactiveHide),
      Rx(div(currentPageFiles().map(FileListItem(_, selectedFile))))
    )
  }

  def changeSortType(): Unit = {
    if (!sortDesc.now) {
      sortDesc() = true
    } else {
      sortType() = (sortType.now + 1) % sortTypes.length
      sortDesc() = false
    }
  }
}

