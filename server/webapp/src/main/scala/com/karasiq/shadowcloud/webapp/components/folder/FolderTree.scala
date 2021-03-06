package com.karasiq.shadowcloud.webapp.components.folder

import com.karasiq.shadowcloud.model.utils.IndexScope
import com.karasiq.shadowcloud.model.{File, Path}
import com.karasiq.shadowcloud.webapp.components.common.{AppIcons, Toastr}
import com.karasiq.shadowcloud.webapp.context.AppContext.BootstrapContext._
import com.karasiq.shadowcloud.webapp.context.AppContext.Implicits._
import com.karasiq.shadowcloud.webapp.context.AppContext.JsExecutionContext
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.controllers.FolderController
import com.karasiq.shadowcloud.webapp.styles.FolderTreeStyles
import com.karasiq.shadowcloud.webapp.utils.RxUtils
import org.scalajs.dom.DragEvent
import org.scalajs.dom.raw.DragEffect
import rx._
import scalaTags.all._

object FolderTree {
  def apply(path: Path)(implicit context: AppContext, folderContext: FolderContext, folderController: FolderController): FolderTree = {
    new FolderTree(path)
  }

  private def isOpened(path: Path)(implicit folderContext: FolderContext): Boolean = {
    folderContext.selected.now.startsWith(path)
  }

  private def toPathString(path: Path)(implicit fc: FolderContext, context: AppContext): String = {
    if (path.isRoot) {
      s"(${fc.regionId.capitalize})"
      // context.locale.rootPath
    } else if (path.name.isEmpty) {
      context.locale.emptyPath
    } else {
      path.name
    }
  }
}

class FolderTree(val path: Path)(implicit context: AppContext, folderContext: FolderContext, _folderController: FolderController)
    extends BootstrapHtmlComponent {

  import folderContext.regionId

  // -----------------------------------------------------------------------
  // State
  // -----------------------------------------------------------------------
  val opened                = Var(FolderTree.isOpened(path))
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
    val newTrees     = (paths -- trees.keySet).map(path ⇒ (path, FolderTree(path)))
    val deletedPaths = trees.keySet -- paths
    trees -- deletedPaths ++ newTrees
  }

  private[this] lazy val subFoldersRx = subFolderMapRx.map(_.toSeq.sortBy(_._1).map(_._2))

  // -----------------------------------------------------------------------
  // Logic
  // -----------------------------------------------------------------------
  implicit val folderController = FolderController.inherit(
    onAddFolder = folder ⇒ if (folder.path.parent == path) opened() = true,
    onDeleteFolder = folder ⇒
      if (folder.path.parent == path) {
        deleted() = true
        folderRx.kill()
      }
  )(_folderController)

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
      folderController.update(path)
      Toastr.success(s"$filePath successfully copied to $path")
    }
  }

  private[this] def copyFile(file: File, readScope: IndexScope): Unit = {
    val newFileSet = context.api.copyFile(regionId, file, file.path.withParent(path), readScope)
    newFileSet.foreach{ _ ⇒
      folderController.update(path)
      Toastr.success(s"${file.path} successfully copied to $path")
    }
  }

  private[this] def copyFolder(folderPath: Path, readScope: IndexScope): Unit = {
    val newPath = folderPath.withParent(path)
    context.api.copyFolder(regionId, folderPath, newPath, readScope).foreach { _ ⇒
      folderController.update(newPath.parent)
      folderController.update(newPath)
      Toastr.success(s"$folderPath successfully copied to $path")
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

      case values ⇒
        System.err.println(s"Invalid drag-and-drop request: $values")
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
      val isOpened   = opened()

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
        val actions = FolderActions(regionId, path)(context, folderContext, folderController)
        small(actions, FolderTreeStyles.folderActions, folderContext.scope.map(_ == IndexScope.Current).reactiveShow)
      } else {
        Bootstrap.noContent
      }

      span(
        a(href := "#", icon, styles, marginRight := 2.px, onclick := Callback.onClick(_ ⇒ opened() = !opened.now)),
        a(href := "#", FolderTree.toPathString(path), styles, dragAndDropHandlers, onclick := Callback.onClick { _ ⇒
          folderContext.selected() = path
          opened() = true
        }),
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
