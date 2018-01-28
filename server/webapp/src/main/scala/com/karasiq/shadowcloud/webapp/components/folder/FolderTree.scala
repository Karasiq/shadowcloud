package com.karasiq.shadowcloud.webapp.components.folder

import scala.language.postfixOps

import org.scalajs.dom.DragEvent
import org.scalajs.dom.raw.DragEffect
import rx._

import com.karasiq.shadowcloud.webapp.context.AppContext.BootstrapContext._
import scalaTags.all._

import com.karasiq.shadowcloud.model.{File, Path}
import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.context.AppContext.Implicits._
import com.karasiq.shadowcloud.webapp.controllers.FolderController
import com.karasiq.shadowcloud.webapp.styles.FolderTreeStyles
import com.karasiq.shadowcloud.webapp.utils.RxUtils

object FolderTree {
  def apply(path: Path)(implicit context: AppContext, folderContext: FolderContext): FolderTree = {
    new FolderTree(path)
  }

  private def isOpened(path: Path)(implicit folderContext: FolderContext): Boolean = {
    folderContext.selected.now.startsWith(path)
  }

  private def toPathString(path: Path)(implicit context: AppContext): String = {
    if (path.isRoot) {
      context.locale.rootPath
    } else if (path.name.isEmpty) {
      context.locale.emptyPath
    } else {
      path.name
    }
  }
}

class FolderTree(val path: Path)(implicit context: AppContext, folderContext: FolderContext) extends BootstrapHtmlComponent {
  import folderContext.regionId

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  val opened = Var(FolderTree.isOpened(path))
  private[this] val deleted = Var(false)

  // -----------------------------------------------------------------------
  // Context
  // -----------------------------------------------------------------------
  private[this] lazy val folderRx = RxUtils.toFolderRx(path)

  private[this] lazy val subFolderNamesRx = Rx {
    val rootFolder = folderRx()
    val subFolders = rootFolder.folders
    for (subFolder ← subFolders) yield rootFolder.path / subFolder
  }

  private[this] lazy val subFolderMapRx = subFolderNamesRx.fold(Map.empty[Path, FolderTree]) { (trees, paths) ⇒
    val newTrees = (paths -- trees.keySet).map(path ⇒ (path, FolderTree(path)))
    val deletedPaths = trees.keySet -- paths
    trees -- deletedPaths ++ newTrees
  }

  private[this] lazy val subFoldersRx = subFolderMapRx.map(_.toSeq.sortBy(_._1).map(_._2))

  // -----------------------------------------------------------------------
  // Logic
  // -----------------------------------------------------------------------
  implicit val controller = FolderController(
    path ⇒ folderContext.update(path),
    folder ⇒ {
      if (folder.path.parent == path) opened() = true
      folderContext.update(folder.path.parent)
    },
    folder ⇒ {
      folderContext.update(folder.path.parent)
      if (folder.path.parent == path) {
        deleted() = true
        folderRx.kill()
      }
    }
  )

  def renderTag(md: ModifierT*): TagT = {
    div(
      Rx[Frag](if (deleted()) {
        Bootstrap.noContent
      } else {
        div(renderLink(), renderContent(), md)
      })
    )
  }

  // -----------------------------------------------------------------------
  // Drag and drop
  // -----------------------------------------------------------------------
  private[this] def copyFiles(filePath: Path, readScope: IndexScope): Unit = {
    context.api.copyFiles(regionId, filePath, filePath.withParent(path), readScope).foreach { _ ⇒
      controller.update(path)
    }
  }

  private[this] def copyFile(file: File, readScope: IndexScope): Unit = {
    val newFileSet = context.api.copyFile(regionId, file, file.path.withParent(path), readScope)
    newFileSet.foreach(_ ⇒ controller.update(path))
  }

  private[this] def copyFolder(folderPath: Path, readScope: IndexScope): Unit = {
    val newPath = folderPath.withParent(path)
    context.api.copyFolder(regionId, folderPath, newPath, readScope).foreach { _ ⇒
      controller.update(newPath.parent)
      controller.update(newPath)
    }
  }

  private[this] def processDropEvent(ev: DragEvent): Unit = {
    val dataTransfer = ev.dataTransfer
    // println(DragAndDrop.DataTransferOps(dataTransfer).toMap)
    val readScope = DragAndDrop.getScope(ev.dataTransfer).getOrElse(IndexScope.default)
    (DragAndDrop.getRegionId(dataTransfer), DragAndDrop.getPath(dataTransfer), DragAndDrop.getFile(dataTransfer)) match {
      case (Some(regionId), _, Some(file)) if regionId == folderContext.regionId ⇒
        ev.preventDefault()
        copyFile(file, readScope)

      case (Some(regionId), Some(path), _) if regionId == folderContext.regionId ⇒
        ev.preventDefault()
        if (DragAndDrop.isFile(dataTransfer)) copyFiles(path, readScope) else copyFolder(path, readScope)

      case _ ⇒
        // Invalid
    }
  }

  // -----------------------------------------------------------------------
  // Components
  // -----------------------------------------------------------------------
  private[this] def renderLink(): ModifierT = {
    def dragAndDropHandlers: ModifierT = {
      Seq(
        draggable,
        dropzone := "copy",
        ondragstart := { (ev: DragEvent) ⇒
          DragAndDrop.addFolderContext(ev.dataTransfer)
          DragAndDrop.addFolderPath(ev.dataTransfer, path)
        },
        ondragover := { (ev: DragEvent) ⇒
          val isCopyable = true // DragAndDrop.isCopyable(ev.dataTransfer) && !DragAndDrop.decodePath(ev.dataTransfer).contains(path)
          if (isCopyable) {
            ev.dataTransfer.dropEffect = DragEffect.Copy
            ev.preventDefault()
          }
        },
        ondrop := processDropEvent _
      )
    }

    Rx {
      val isSelected = folderContext.selected() == path
      val isOpened = opened()

      val icon = if (isOpened) {
        AppIcons.folderOpen
      } else {
        AppIcons.folder
      }

      val styles = if (isSelected) {
        Seq(Bootstrap.textStyle.success, fontWeight.bold)
      } else {
        Seq(Bootstrap.textStyle.primary)
      }

      val actions: Frag = if (isSelected) {
        small(FolderActions(regionId, path), FolderTreeStyles.folderActions, folderContext.scope.map(_ == IndexScope.Current).reactiveShow)
      } else {
        Bootstrap.noContent
      }

      span(
        a(href := "#", icon, styles, marginRight := 2.px, onclick := Callback.onClick(_ ⇒ opened() = !opened.now)),
        a(href := "#", FolderTree.toPathString(path), styles, dragAndDropHandlers, onclick := Callback.onClick(_ ⇒ folderContext.selected() = path)),
        actions
      )
    }
  }

  private[this] def renderContent(): Rx[Frag] = {
    Rx[Frag] {
      if (opened()) {
        div(Rx(div(subFoldersRx.apply().map(_.renderTag()))), FolderTreeStyles.subTree)
      } else {
        Bootstrap.noContent
      }
    }
  }
}


