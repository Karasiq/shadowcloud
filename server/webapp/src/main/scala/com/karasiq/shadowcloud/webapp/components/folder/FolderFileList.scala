package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.bootstrap.Bootstrap.default._
import scalaTags.all._

import org.scalajs.dom.DragEvent
import rx.{Rx, Var}

import com.karasiq.common.memory.MemorySize
import com.karasiq.shadowcloud.index.files.FileVersions
import com.karasiq.shadowcloud.model.File
import com.karasiq.shadowcloud.webapp.components.file.FileDownloadLink
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}

object FolderFileList {
  def apply(files: Rx[Set[File]], flat: Boolean = true)(implicit context: AppContext, folderContext: FolderContext): FolderFileList = {
    new FolderFileList(files, flat)
  }
}

class FolderFileList(filesRx: Rx[Set[File]], flat: Boolean)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  val selectedFile = Var(None: Option[File])

  def renderTag(md: ModifierT*): TagT = {
    val filterRx = Var("")
    val heading = Rx {
      import OrderingSelector.orderingLink
      if (flat) {
        Seq[Modifier](
          orderingLink(0)(context.locale.name),
          orderingLink(1)(context.locale.size),
          orderingLink(2)(context.locale.modifiedDate)
        )
      } else {
        Seq[Modifier](
          context.locale.fileId,
          orderingLink(0)(context.locale.name),
          orderingLink(1)(context.locale.size),
          orderingLink(2)(context.locale.modifiedDate)
        )
      }
    }

    val rows = Rx {
      val allFiles = filesRx()

      val filteredFiles = {
        val filter = filterRx()
        if (filter.isEmpty) allFiles else allFiles.filter(_.path.name.toLowerCase.contains(filter.toLowerCase))
      }

      val sortedFiles = {
        val fileSeq = if (flat) {
          FileVersions.toFlatDirectory(filteredFiles)
        } else {
          filteredFiles.toVector
        }

        val sortFunction = OrderingSelector.sortFunctionRx()
        sortFunction(fileSeq)
      }

      sortedFiles.map { file ⇒
        val dragAndDropHandlers = Seq[Modifier](
          draggable,
          ondragstart := { (ev: DragEvent) ⇒
            DragAndDrop.addFolderContext(ev.dataTransfer)
            if (flat)
              DragAndDrop.addFilePath(ev.dataTransfer, file.path)
            else
              DragAndDrop.addFileHandle(ev.dataTransfer, file)
          }
        )

        val content1 = if (flat) {
          Seq[Modifier](
            FileDownloadLink(file)(file.path.name)
          )
        } else {
          Seq[Modifier](
            FileDownloadLink(file, useId = true)(file.id.toString),
            file.path.name
          )
        }

        val content2 = Seq[Modifier](
          MemorySize.toString(file.checksum.size),
          context.timeFormat.timestamp(file.timestamp.lastModified)
        )

        TableRow(
          content1 ++ content2,
          dragAndDropHandlers,
          TableRowStyle.active.styleClass.map(_.classIf(selectedFile.map(_.exists(_ == file)))),
          onclick := Callback.onClick(_ ⇒ selectedFile() = Some(file))
        )
      }
    }

    val table = PagedTable(heading, rows)
    div(
      GridSystem.mkRow(Form(FormInput.text("", filterRx.reactiveInput))),
      GridSystem.mkRow(table.renderTag(md:_*))
    )
  }

  private[this] object FileOrdering {
    type FileOrdering[T] = (File ⇒ T, Ordering[T])
    type SortFunction = Seq[File] ⇒ Seq[File]

    val name: FileOrdering[String] = ((_: File).path.name.toLowerCase, Ordering[String])
    val size: FileOrdering[Long] = ((_: File).checksum.size, Ordering[Long])
    val date: FileOrdering[Long] = ((_: File).timestamp.lastModified, Ordering[Long])

    implicit def toSortFunction[T](order: FileOrdering[T])(files: Seq[File]): Seq[File] = {
      files.sortBy(f ⇒ (order._1(f), f.revision))(Ordering.Tuple2(order._2, Ordering[Long].reverse))
    }
  }

  private[this] object OrderingSelector {
    import FileOrdering._
    val orderings = Seq[SortFunction](
      name,
      size,
      date
    )

    val currentOrderingIndex = Var(0)
    val reverseOrdering = Var(false)

    val sortFunctionRx = Rx[SortFunction] {
      val sortFunction = orderings(currentOrderingIndex())
      if (reverseOrdering()) sortFunction.andThen(_.reverse) else sortFunction
    }

    def setOrdering(index: Int) = {
      if (currentOrderingIndex.now == index) {
        reverseOrdering() = !reverseOrdering.now
      } else {
        reverseOrdering() = false
        currentOrderingIndex() = index
      }
    }

    def orderingLink(i: Int) = {
      val icon = Rx[Frag] {
        if (currentOrderingIndex() == i) Icon.faFw(if (reverseOrdering()) "caret-down" else "caret-up")
        else Bootstrap.noContent
      }

      span(cursor.pointer, icon, onclick := Callback.onClick(_ ⇒ setOrdering(i)))
    }
  }
}
