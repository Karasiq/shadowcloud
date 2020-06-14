package com.karasiq.shadowcloud.webapp.components.folder

import rx.{Rx, Var}

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

private[folder] object PreviewsFileList {
  def apply(files: Rx[Seq[File]], selectedFile: Var[Option[File]])
           (implicit context: AppContext, fc: FolderContext): PreviewsFileList = {
    new PreviewsFileList(files, selectedFile)
  }
}

private[folder] class PreviewsFileList(files: Rx[Seq[File]], selectedFile: Var[Option[File]])
                                      (implicit context: AppContext, fc: FolderContext) extends BootstrapHtmlComponent {
  def renderTag(md: ModifierT*): TagT = {
    val filterField = Form(FormInput.text("", sorting.filter.reactiveInput))

    val sortLine = Rx {
      span(
        Icon(if (sorting.sortDesc()) "triangle-bottom" else "triangle-top"),
        b(sorting.sortTypes(sorting.sortType())),
        cursor.pointer,
        onclick := Callback.onClick(_ ⇒ sorting.changeSortType())
      )
    }

    div(
      div(filterField, files.map(_.isEmpty).reactiveHide),
      // hr(files.map(_.isEmpty).reactiveHide),
      div(textAlign.center, pagination.pageSelector, pagination.pages.map(_ <= 1).reactiveHide),
      div(sortLine, sorting.sortedFiles.map(_.length <= 1).reactiveHide),
      // hr(files.map(_.isEmpty).reactiveHide),
      Rx(GridSystem.containerFluid(pagination.currentPageFiles().map(PreviewsFileListItem(_, selectedFile)):_*))
    )
  }

  private[this] object sorting {
    val sortTypes = Seq(context.locale.name, context.locale.size, context.locale.modifiedDate)
    val sortType = Var(0)
    val sortDesc = Var(false)
    val filter = Var("")

    lazy val sortedFiles = Rx {
      val allFiles = files()
      val filtered = {
        val filterStr = filter()
        if (filterStr.isEmpty) allFiles
        else allFiles.filter(_.path.name.toLowerCase.contains(filterStr.toLowerCase))
      }
      val sorted = sortTypes(sortType()) match {
        case s if s == context.locale.name ⇒ filtered.sortBy(_.path.name)
        case s if s == context.locale.size ⇒ filtered.sortBy(_.checksum.size)
        case s if s == context.locale.modifiedDate ⇒ filtered.sortBy(_.timestamp.lastModified)
        case _ ⇒ filtered
      }
      if (sortDesc()) sorted.reverse else sorted
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

  private[this] object pagination {
    val rowsPerPage = 10
    lazy val pages = sorting.sortedFiles.map(fs ⇒ (fs.length.toDouble / rowsPerPage).ceil.toInt)

    lazy val pageSelector = PageSelector(pages)

    lazy val currentPageFiles = Rx {
      val page = pageSelector.currentPage() min pages()
      sorting.sortedFiles().slice(rowsPerPage * (page - 1), rowsPerPage * (page - 1) + rowsPerPage)
    }
  }
}

