package com.karasiq.shadowcloud.webapp.components.folder

import scala.language.postfixOps

import rx._

import com.karasiq.shadowcloud.webapp.context.AppContext.BootstrapContext._
import scalaTags.all._

import com.karasiq.shadowcloud.model.{Folder, Path, RegionId}
import com.karasiq.shadowcloud.webapp.components.common.AppIcons
import com.karasiq.shadowcloud.webapp.components.folder.FolderTree.FolderController
import com.karasiq.shadowcloud.webapp.context.{AppContext, FolderContext}
import com.karasiq.shadowcloud.webapp.context.AppContext.Implicits._
import com.karasiq.shadowcloud.webapp.styles.FolderTreeStyles
import com.karasiq.shadowcloud.webapp.utils.{HasUpdate, RxUtils}

object FolderTree {
  def apply(regionId: RegionId, path: Path)(implicit context: AppContext, folderContext: FolderContext): FolderTree = {
    new FolderTree(regionId, path)
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

  private[folder] trait FolderController extends HasUpdate {
    def addFolder(folder: Folder): Unit
    def deleteFolder(folder: Folder): Unit
  }

  private[folder] object FolderController {
    def apply(onUpdate: () ⇒ Unit, onAddFolder: Folder ⇒ Unit, onDeleteFolder: Folder ⇒ Unit): FolderController = {
      new FolderController {
        def addFolder(folder: Folder): Unit = onAddFolder(folder)
        def deleteFolder(folder: Folder): Unit = onDeleteFolder(folder)
        def update(): Unit = onUpdate()
      }
    }
  }
}

class FolderTree(regionId: RegionId, path: Path)
                (implicit context: AppContext, folderContext: FolderContext)
  extends BootstrapHtmlComponent {

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
    val newTrees = (paths -- trees.keySet).map(path ⇒ (path, FolderTree(regionId, path)))
    val deletedPaths = trees.keySet -- paths
    trees -- deletedPaths ++ newTrees
  }

  private[this] lazy val subFoldersRx = subFolderMapRx.map(_.toSeq.sortBy(_._1).map(_._2))

  // -----------------------------------------------------------------------
  // Components
  // -----------------------------------------------------------------------
  private[this] implicit val controller = FolderController(
    () ⇒ folderContext.update(path),
    folder ⇒ {
      folderContext.update(folder.path.parent)
      if (folder.path.parent == path) opened() = true
    },
    folder ⇒ {
      folderContext.update(folder.path.parent)
      if (folder.path.parent == path) deleted() = true
    }
  )

  private[this] val link = renderLink()
  private[this] val content = renderContent()

  def renderTag(md: ModifierT*): TagT = {
    div(
      Rx[Frag](if (deleted()) {
        Bootstrap.noContent
      } else {
        div(link, content)
      }),
      md
    )
  }

  private[this] def renderLink(): ModifierT = {
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
        small(Bootstrap.nbsp, FolderActions(regionId, path), FolderTreeStyles.folderActions)
      } else {
        Bootstrap.noContent
      }

      span(
        a(href := "#", icon, styles, onclick := Callback.onClick(_ ⇒ opened() = !opened.now)),
        Bootstrap.nbsp,
        a(href := "#", FolderTree.toPathString(path), styles, onclick := Callback.onClick(_ ⇒ folderContext.selected() = path)),
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


